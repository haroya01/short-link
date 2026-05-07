resource "aws_route53_zone" "this" {
  count = var.create_route53_zone ? 1 : 0
  name  = var.domain
}

data "aws_route53_zone" "this" {
  count        = var.create_route53_zone ? 0 : 1
  name         = var.domain
  private_zone = false
}

locals {
  zone_id = var.create_route53_zone ? aws_route53_zone.this[0].zone_id : data.aws_route53_zone.this[0].zone_id
}

resource "aws_route53_record" "api" {
  zone_id = local.zone_id
  name    = local.api_fqdn
  type    = "A"

  alias {
    name                   = aws_lb.this.dns_name
    zone_id                = aws_lb.this.zone_id
    evaluate_target_health = true
  }
}
