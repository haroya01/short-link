resource "aws_db_subnet_group" "this" {
  name       = "${local.name_prefix}-db"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_parameter_group" "mysql8" {
  name   = "${local.name_prefix}-mysql8"
  family = "mysql8.0"

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_0900_ai_ci"
  }
}

resource "aws_db_instance" "this" {
  identifier     = "${local.name_prefix}-mysql"
  engine         = "mysql"
  engine_version = "8.0"
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage_gb
  max_allocated_storage = 50
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.mysql8.name

  multi_az                     = false
  publicly_accessible          = false
  auto_minor_version_upgrade   = true
  backup_retention_period      = 3
  backup_window                = "18:00-19:00"
  maintenance_window           = "sun:19:30-sun:20:30"
  performance_insights_enabled = false
  deletion_protection          = true
  skip_final_snapshot          = false
  final_snapshot_identifier    = "${local.name_prefix}-mysql-final"

  apply_immediately = false
}
