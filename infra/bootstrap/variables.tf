variable "region" {
  description = "AWS region for bootstrap resources."
  type        = string
  default     = "ap-northeast-1"
}

variable "github_owner" {
  description = "GitHub owner (user or org) that hosts the repository."
  type        = string
  default     = "haroya01"
}

variable "github_repo" {
  description = "GitHub repository name."
  type        = string
  default     = "short-link"
}
