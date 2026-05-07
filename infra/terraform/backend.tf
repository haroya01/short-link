terraform {
  backend "s3" {
    bucket         = "kurl-tfstate-REPLACE_ACCOUNT_ID"
    key            = "prod/terraform.tfstate"
    region         = "ap-northeast-1"
    dynamodb_table = "kurl-tflock"
    encrypt        = true
  }
}
