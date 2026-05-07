resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name_prefix}-app"
  retention_in_days = var.log_retention_days
}

resource "aws_ecs_cluster" "this" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

locals {
  app_env = [
    { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
    { name = "TZ", value = "UTC" },
    { name = "DB_URL", value = "jdbc:mysql://${aws_db_instance.this.address}:3306/${var.db_name}?useSSL=true&requireSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" },
    { name = "DB_USERNAME", value = var.db_username },
    { name = "REDIS_HOST", value = aws_elasticache_replication_group.this.primary_endpoint_address },
    { name = "REDIS_PORT", value = "6379" },
    { name = "REDIS_SSL", value = "true" },
    { name = "SHORT_LINK_BASE_URL", value = "https://${local.api_fqdn}" },
    { name = "SHORT_LINK_FRONTEND_BASE_URL", value = var.frontend_base_url },
    { name = "COOKIE_SECURE", value = "true" },
    { name = "SAFE_BROWSING_ENABLED", value = "true" },
    { name = "BOT_HEURISTIC_RATE_THRESHOLD", value = "5" },
  ]

  app_secrets_arns = {
    DB_PASSWORD                      = aws_ssm_parameter.app["db_password"].arn
    REDIS_PASSWORD                   = aws_ssm_parameter.app["redis_auth_token"].arn
    JWT_PRIVATE_KEY                  = aws_ssm_parameter.app["jwt_private_key"].arn
    JWT_PUBLIC_KEY                   = aws_ssm_parameter.app["jwt_public_key"].arn
    GOOGLE_CLIENT_ID                 = aws_ssm_parameter.app["google_client_id"].arn
    GOOGLE_CLIENT_SECRET             = aws_ssm_parameter.app["google_client_secret"].arn
    SAFE_BROWSING_API_KEY            = aws_ssm_parameter.app["safe_browsing_api_key"].arn
    SHORT_LINK_BOOTSTRAP_ADMIN_EMAIL = aws_ssm_parameter.app["bootstrap_admin_email"].arn
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = "${local.name_prefix}-app"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.fargate_cpu
  memory                   = var.fargate_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name      = "app"
      image     = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
      essential = true

      portMappings = [
        { containerPort = 8080, protocol = "tcp" },
      ]

      environment = local.app_env

      secrets = [
        for k, arn in local.app_secrets_arns : {
          name      = k
          valueFrom = arn
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = var.region
          awslogs-stream-prefix = "app"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 90
      }
    }
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }
}

resource "aws_ecs_service" "app" {
  name            = "${local.name_prefix}-app"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = 8080
  }

  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 120

  depends_on = [aws_lb_listener.https]

  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }
}
