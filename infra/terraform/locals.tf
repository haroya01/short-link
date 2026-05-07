locals {
  name_prefix = "${var.project}-${var.env}"
  api_fqdn    = "${var.api_subdomain}.${var.domain}"
}
