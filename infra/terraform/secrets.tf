resource "random_password" "db" {
  length  = 32
  special = false
}

locals {
  app_secret_params = {
    db_password           = { value = random_password.db.result, autogen = true }
    jwt_private_key       = { value = "REPLACE_PEM", autogen = false }
    jwt_public_key        = { value = "REPLACE_PEM", autogen = false }
    google_client_id      = { value = "REPLACE", autogen = false }
    google_client_secret  = { value = "REPLACE", autogen = false }
    safe_browsing_api_key = { value = "REPLACE", autogen = false }
    bootstrap_admin_email = { value = "REPLACE", autogen = false }
  }
}

resource "aws_ssm_parameter" "app" {
  for_each = local.app_secret_params

  name  = "/${local.name_prefix}/${each.key}"
  type  = "SecureString"
  value = each.value.value

  lifecycle {
    ignore_changes = [value]
  }
}
