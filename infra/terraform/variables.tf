variable "project" {
  description = "Project tag prefix (e.g. \"kurl\")."
  type        = string
  default     = "kurl"
}

variable "env" {
  description = "Environment name (single stack today; named for future dev/prod split)."
  type        = string
  default     = "prod"
}

variable "region" {
  description = "AWS region."
  type        = string
  default     = "ap-northeast-1"
}

variable "azs" {
  description = "Availability Zones to spread subnets across."
  type        = list(string)
  default     = ["ap-northeast-1a", "ap-northeast-1c"]
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.30.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (one per AZ)."
  type        = list(string)
  default     = ["10.30.0.0/24", "10.30.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets (one per AZ)."
  type        = list(string)
  default     = ["10.30.10.0/24", "10.30.11.0/24"]
}

variable "domain" {
  description = "Apex domain managed in Route53."
  type        = string
  default     = "kurl.me"
}

variable "api_subdomain" {
  description = "Subdomain that fronts the backend ALB."
  type        = string
  default     = "api"
}

variable "frontend_base_url" {
  description = "Public URL of the frontend (used for OAuth redirect targets)."
  type        = string
  default     = "https://kurl.me"
}

variable "create_route53_zone" {
  description = "Set to true if Terraform should create the hosted zone. If the zone already exists, leave false and import it."
  type        = bool
  default     = false
}

variable "fargate_cpu" {
  description = "Fargate task vCPU (units of 1024). 512 = 0.5 vCPU."
  type        = number
  default     = 512
}

variable "fargate_memory" {
  description = "Fargate task memory in MiB."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "How many ECS tasks to run."
  type        = number
  default     = 1
}

variable "image_tag" {
  description = "Image tag to deploy. Defaults to \"bootstrap\" so the first apply uses the placeholder image; CI overrides on each push."
  type        = string
  default     = "bootstrap"
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage_gb" {
  description = "RDS allocated storage in GB."
  type        = number
  default     = 20
}

variable "db_username" {
  description = "RDS master username."
  type        = string
  default     = "kurl"
}

variable "db_name" {
  description = "Initial database name created on the instance."
  type        = string
  default     = "short_link"
}

variable "redis_node_type" {
  description = "ElastiCache node type."
  type        = string
  default     = "cache.t4g.micro"
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention in days."
  type        = number
  default     = 7
}
