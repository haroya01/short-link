output "alb_dns_name" {
  description = "Public ALB DNS name."
  value       = aws_lb.this.dns_name
}

output "api_url" {
  description = "Final HTTPS URL the backend is reachable at."
  value       = "https://${local.api_fqdn}"
}

output "ecr_repository_url" {
  description = "ECR repository URL the deploy workflow pushes to."
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.this.name
}

output "ecs_service_name" {
  description = "ECS service name."
  value       = aws_ecs_service.app.name
}

output "ecs_task_family" {
  description = "ECS task definition family."
  value       = aws_ecs_task_definition.app.family
}

output "rds_endpoint" {
  description = "RDS endpoint host (no port)."
  value       = aws_db_instance.this.address
}

output "redis_endpoint" {
  description = "ElastiCache primary endpoint host."
  value       = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "ssm_parameter_prefix" {
  description = "Prefix under which app secrets live in SSM Parameter Store."
  value       = "/${local.name_prefix}"
}

output "name_servers" {
  description = "If Terraform created the hosted zone, the NS records to set at your registrar. Empty if zone existed already."
  value       = var.create_route53_zone ? aws_route53_zone.this[0].name_servers : []
}
