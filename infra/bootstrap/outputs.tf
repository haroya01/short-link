output "tfstate_bucket" {
  description = "S3 bucket for Terraform state of the main stack."
  value       = aws_s3_bucket.tfstate.bucket
}

output "tflock_table" {
  description = "DynamoDB table used for state locking."
  value       = aws_dynamodb_table.tflock.name
}

output "github_deploy_role_arn" {
  description = "IAM role ARN to assume from GitHub Actions via OIDC."
  value       = aws_iam_role.github_deploy.arn
}
