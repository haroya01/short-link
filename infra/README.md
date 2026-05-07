# short-link AWS 인프라

ap-northeast-1 (Tokyo) 단일 prod 스택. 도메인 `kurl.me`, 백엔드 `https://api.kurl.me`.

## 구성

- VPC: 2 AZ × (public/private) — NAT Gateway 없음 (비용 절감)
- ECS Fargate (ARM64, 0.5 vCPU / 1 GB) × 1 task — public subnet, public IP 자동 할당
- ALB: HTTPS만, ACM cert (DNS validation)
- RDS MySQL 8.0 `db.t4g.micro`, single-AZ, gp3 20 GB, private subnet
- ElastiCache Redis 7.1 `cache.t4g.micro`, single-node, private subnet
- ECR (`kurl-prod-app`)
- Route53 (`kurl.me`)
- SSM Parameter Store: 시크릿
- CloudWatch Logs `/ecs/kurl-prod-app` (7 days)

월 비용: 약 $65 (ALB $18 + Fargate $19 + RDS $15 + ElastiCache $12 + 기타).

## 디렉터리

```
infra/
├── bootstrap/       # 1회성: tfstate 버킷, lock, GitHub OIDC, deploy role
└── terraform/       # 메인 스택
```

## 1. 사전 준비

- AWS CLI에 prod 계정 자격 증명 구성 (`aws sts get-caller-identity` 통과)
- Terraform 1.6+, jq
- Google Cloud Console에서 OAuth client 생성. redirect URI: `https://api.kurl.me/login/oauth2/code/google`
- Google Safe Browsing API key
- MaxMind GeoLite2 license key (이미 CI에서 사용 중)
- 도메인 `kurl.me`는 Route53에 hosted zone이 있거나, 외부 등록기관에서 NS를 Route53으로 위임해야 함

## 2. Bootstrap (한 번만)

S3 backend, DynamoDB lock, GitHub OIDC provider, deploy role 생성.

```bash
cd infra/bootstrap
terraform init
terraform apply
```

출력으로 나오는 값을 메모:

- `tfstate_bucket` → 메인 스택 backend.tf에 입력
- `github_deploy_role_arn` → GitHub repo `AWS_DEPLOY_ROLE_ARN` secret으로 등록

## 3. 메인 스택 설정

### 3-1. backend.tf 수정

`infra/terraform/backend.tf` 의 `REPLACE_ACCOUNT_ID`를 본인 계정 ID로 교체.

```hcl
bucket = "kurl-tfstate-123456789012"
```

### 3-2. Route53 hosted zone

`kurl.me`에 hosted zone이 이미 있으면 import:

```bash
cd infra/terraform
terraform init
terraform import 'data.aws_route53_zone.this[0]' kurl.me
```

> 이미 `data` 리소스로 lookup하므로 import 불필요. zone이 **없을** 때는 `var.create_route53_zone=true`로 변경하고, apply 후 출력된 NS 4개를 도메인 등록기관에서 위임 NS로 설정.

### 3-3. 첫 apply (이미지 없는 상태)

ECR이 비어 있으므로 ECS 태스크는 일단 풀에 실패한다 (이건 정상). VPC/RDS/ElastiCache/ALB/ACM 등 모든 인프라는 생성된다.

```bash
terraform init
terraform apply
```

RDS·ElastiCache 생성에 10~15분 소요.

### 3-4. SSM 시크릿 채우기

`db_password`, `redis_auth_token` 두 개는 random_password로 자동 생성됨. 나머지는 placeholder `REPLACE`로 채워져 있으니 직접 업데이트:

```bash
PREFIX=/kurl-prod

# JWT RSA keypair 생성
openssl genrsa -out jwt-private.pem 2048
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem

aws ssm put-parameter --overwrite --type SecureString \
  --name $PREFIX/jwt_private_key --value "$(cat jwt-private.pem)"
aws ssm put-parameter --overwrite --type SecureString \
  --name $PREFIX/jwt_public_key --value "$(cat jwt-public.pem)"

aws ssm put-parameter --overwrite --type SecureString \
  --name $PREFIX/google_client_id --value "<google-oauth-client-id>"
aws ssm put-parameter --overwrite --type SecureString \
  --name $PREFIX/google_client_secret --value "<google-oauth-client-secret>"
aws ssm put-parameter --overwrite --type SecureString \
  --name $PREFIX/safe_browsing_api_key --value "<safe-browsing-api-key>"
aws ssm put-parameter --overwrite --type SecureString \
  --name $PREFIX/bootstrap_admin_email --value "<your-admin-email>"

rm jwt-private.pem jwt-public.pem
```

> Terraform은 `lifecycle.ignore_changes = [value]`로 SSM 값 변경을 추적하지 않으니, 이 단계 이후 terraform apply가 값을 되돌리지 않음.

### 3-5. GitHub repo 설정

Settings → Secrets and variables → Actions:

- `AWS_DEPLOY_ROLE_ARN` = bootstrap 출력 값
- `MAXMIND_LICENSE_KEY` (이미 있을 가능성 높음)
- `SUB_TOKEN` (resources 서브모듈 접근용, 이미 있을 가능성)

Settings → Environments → `prod` 생성 (보호 규칙은 자유).

### 3-6. 첫 배포

main 브랜치에 push 또는 Actions에서 `Deploy` 워크플로 수동 실행. 약 5~7분 후 ECS 태스크가 RUNNING이 되고, ALB 헬스체크 통과 후 트래픽 받음.

확인:

```bash
curl -i https://api.kurl.me/actuator/health
```

## 4. RDS DB 초기화

Flyway가 마이그레이션을 자동 실행하므로 별도 스키마 작업 불필요. 첫 배포 시 task 로그(CloudWatch)에서 `Successfully applied N migrations` 확인.

## 5. 운영

### 일시 정지 (비용 절약)

```bash
aws ecs update-service --cluster kurl-prod-cluster --service kurl-prod-app --desired-count 0
```

다시 켤 때 `--desired-count 1`. RDS·ElastiCache는 stop이 single-AZ에서는 7일 한도이므로 장기 정지하려면 destroy.

### 전체 destroy

```bash
cd infra/terraform
# RDS deletion_protection이 켜져 있으니 먼저 끄기
terraform apply -var='deletion_protection_unsafe=true'  # (해당 변수가 있다면)
# 또는 콘솔/CLI로 RDS modify하여 deletion_protection 비활성화 후
terraform destroy
```

> 현재 코드는 `deletion_protection = true` 하드코딩. destroy 전에 `aws rds modify-db-instance --db-instance-identifier kurl-prod-mysql --no-deletion-protection --apply-immediately` 후 destroy.

## 6. 주의사항

- ECS 태스크가 **public subnet**에 있어 ECR 이미지 풀이 가능. 보안 그룹은 ALB만 허용.
- Redis는 transit encryption + auth_token 사용. Spring 측은 `REDIS_SSL=true`로 처리.
- ALB는 HTTP를 HTTPS로 301 리다이렉트.
- Google OAuth redirect URI는 `https://api.kurl.me/login/oauth2/code/google` 하나만 등록하면 됨.
- 프론트엔드는 별도 호스팅 (Vercel 등). 배포되면 `frontend_base_url` 변수와 SSM의 `bootstrap_admin_email`만 갱신하면 됨.
