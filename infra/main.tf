############################
# Terraform & Provider
############################

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

variable "aws_region" {
  description = "AWS region to deploy resources into"
  type        = string
  default     = "ap-northeast-2"
}

provider "aws" {
  region = var.aws_region
}

############################
# Project-level variables
############################

variable "project_name" {
  description = "Logical project name prefix"
  type        = string
  default     = "darkweb-crawler"
}

# 로컬에서 빌드한 Lambda ZIP 경로
# ./gradlew shadowJar 후, 예를 들어:
# zip -j build/lambda/darkweb-crawler-crawler.zip build/libs/darkweb-crawler-all.jar
# zip -j build/lambda/darkweb-crawler-admin-api.zip build/libs/darkweb-crawler-all.jar

variable "crawler_lambda_zip" {
  description = "Path to crawler Lambda ZIP file on local disk"
  type        = string
  default     = "../build/lambda/darkweb-crawler-crawler.zip"
}

variable "admin_lambda_zip" {
  description = "Path to admin API Lambda ZIP file on local disk"
  type        = string
  default     = "../build/lambda/darkweb-crawler-admin-api.zip"
}

# DarkIntel 백엔드용 기본 값 (원하면 나중에 SSM/Secrets로 옮겨도 됨)
variable "backend_base_url" {
  description = "DarkIntel backend base URL (e.g., https://your-darkintel-backend/v1)"
  type        = string
  default     = "https://your-darkintel-backend/v1"
}

variable "backend_api_token" {
  description = "DarkIntel ingest API token (Bearer)"
  type        = string
  default     = "CHANGE_ME_TOKEN"
  sensitive   = true
}

############################
# DynamoDB Tables
############################

# 소스별 상태
resource "aws_dynamodb_table" "source_state" {
  name         = "${var.project_name}_source_state"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "source_id"

  attribute {
    name = "source_id"
    type = "S"
  }

  tags = {
    Project = var.project_name
    Role    = "source_state"
  }
}

# 문서 중복 체크
resource "aws_dynamodb_table" "documents" {
  name         = "${var.project_name}_documents"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "source_id"
  range_key    = "url"

  attribute {
    name = "source_id"
    type = "S"
  }

  attribute {
    name = "url"
    type = "S"
  }

  tags = {
    Project = var.project_name
    Role    = "documents"
  }
}

# DynamoDB 락 (Redis 대신 사용)
resource "aws_dynamodb_table" "locks" {
  name         = "${var.project_name}_locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "lock_id"

  attribute {
    name = "lock_id"
    type = "S"
  }

  tags = {
    Project = var.project_name
    Role    = "locks"
  }
}

# 소스별 스케줄 설정
resource "aws_dynamodb_table" "schedule" {
  name         = "${var.project_name}_schedule"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "source_id"

  attribute {
    name = "source_id"
    type = "S"
  }

  tags = {
    Project = var.project_name
    Role    = "schedule"
  }
}

############################
# IAM Roles & Policies
############################

# 공통 Lambda assume role policy
data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

# CloudWatch Logs 기본 정책
data "aws_iam_policy_document" "lambda_logs" {
  statement {
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = ["arn:aws:logs:${var.aws_region}:*:log-group:/aws/lambda/*"]
  }
}

resource "aws_iam_policy" "lambda_logs" {
  name   = "${var.project_name}-lambda-logs"
  policy = data.aws_iam_policy_document.lambda_logs.json
}

############################
# Crawler Lambda Role
############################

# DynamoDB 접근 권한
data "aws_iam_policy_document" "crawler_dynamo_policy" {
  statement {
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
      "dynamodb:Query",
      "dynamodb:Scan"
    ]
    resources = [
      aws_dynamodb_table.source_state.arn,
      aws_dynamodb_table.documents.arn,
      aws_dynamodb_table.locks.arn,
      aws_dynamodb_table.schedule.arn
    ]
  }
}

resource "aws_iam_policy" "crawler_dynamo_policy" {
  name   = "${var.project_name}-crawler-dynamo"
  policy = data.aws_iam_policy_document.crawler_dynamo_policy.json
}

resource "aws_iam_role" "crawler_lambda_role" {
  name               = "${var.project_name}-crawler-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "crawler_logs_attach" {
  role       = aws_iam_role.crawler_lambda_role.name
  policy_arn = aws_iam_policy.lambda_logs.arn
}

resource "aws_iam_role_policy_attachment" "crawler_dynamo_attach" {
  role       = aws_iam_role.crawler_lambda_role.name
  policy_arn = aws_iam_policy.crawler_dynamo_policy.arn
}

############################
# Admin API Lambda Role
############################

# Admin Lambda가 Crawler Lambda를 호출하고, schedule 테이블을 수정해야 함
data "aws_iam_policy_document" "admin_policy" {
  # Lambda invoke 권한
  statement {
    actions = ["lambda:InvokeFunction"]
    resources = [
      # 나중에 crawler lambda ARN을 참조하기 위해서 아래에서 override
      # 일단 "*"로 두고, resource를 별도로 지정하자
      "arn:aws:lambda:${var.aws_region}:*:function:*"
    ]
  }

  # DynamoDB schedule 테이블 권한
  statement {
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem"
    ]
    resources = [
      aws_dynamodb_table.schedule.arn
    ]
  }
}


resource "aws_iam_policy" "admin_policy" {
  name   = "${var.project_name}-admin"
  policy = data.aws_iam_policy_document.admin_policy.json
}

resource "aws_iam_role" "admin_lambda_role" {
  name               = "${var.project_name}-admin-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "admin_logs_attach" {
  role       = aws_iam_role.admin_lambda_role.name
  policy_arn = aws_iam_policy.lambda_logs.arn
}

resource "aws_iam_role_policy_attachment" "admin_main_attach" {
  role       = aws_iam_role.admin_lambda_role.name
  policy_arn = aws_iam_policy.admin_policy.arn
}

############################
# Lambda Functions
############################

# Crawler Lambda (EventBridge + Admin API 둘 다 여기 호출)
resource "aws_lambda_function" "crawler_lambda" {
  function_name = "${var.project_name}-runner"
  role          = aws_iam_role.crawler_lambda_role.arn
  handler       = "com.darkintel.crawler.lambda.CrawlerLambdaHandler::handleRequest"
  runtime       = "java21"

  filename         = var.crawler_lambda_zip
  source_code_hash = filebase64sha256(var.crawler_lambda_zip)

  timeout = 900 # 15분

  environment {
    variables = {
      DARKWEB_AWS_REGION  = var.aws_region
      DARKWEB_CONFIG_PATH = "/var/task/config.lambda.toml"
      backend_base_url    = var.backend_base_url
      backend_api_token   = var.backend_api_token
    }
  }

  tags = {
    Project = var.project_name
    Role    = "crawler"
  }
}

# Admin API Lambda (API Gateway로 HTTP 호출 → 이 Lambda에서 crawler Lambda async invoke)
resource "aws_lambda_function" "admin_lambda" {
  function_name = "${var.project_name}-admin-api"
  role          = aws_iam_role.admin_lambda_role.arn
  handler       = "com.darkintel.crawler.lambda.AdminApiLambdaHandler::handleRequest"
  runtime       = "java21"

  filename         = var.admin_lambda_zip
  source_code_hash = filebase64sha256(var.admin_lambda_zip)

  timeout = 30

  environment {
    variables = {
      DARKWEB_AWS_REGION             = var.aws_region
      DARKWEB_CRAWLER_LAMBDA_NAME    = aws_lambda_function.crawler_lambda.function_name
      DARKWEB_SCHEDULE_TABLE_NAME    = aws_dynamodb_table.schedule.name
      DARKWEB_SOURCE_STATE_TABLE     = aws_dynamodb_table.source_state.name
      DARKWEB_DOCUMENTS_TABLE        = aws_dynamodb_table.documents.name
      DARKWEB_LOCKS_TABLE            = aws_dynamodb_table.locks.name
    }
  }

  tags = {
    Project = var.project_name
    Role    = "admin-api"
  }
}

############################
# EventBridge (스케줄 실행)
############################

# 예: 5분마다 크롤러 Lambda 실행
resource "aws_cloudwatch_event_rule" "crawler_schedule" {
  name                = "${var.project_name}-schedule"
  description         = "Periodic schedule for darkweb crawler"
  schedule_expression = "rate(5 minutes)"
}

resource "aws_cloudwatch_event_target" "crawler_schedule_target" {
  rule      = aws_cloudwatch_event_rule.crawler_schedule.name
  target_id = "crawler-lambda"
  arn       = aws_lambda_function.crawler_lambda.arn
}

# EventBridge가 Lambda 호출할 수 있도록 권한 부여
resource "aws_lambda_permission" "allow_eventbridge_invoke" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.crawler_lambda.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.crawler_schedule.arn
}

############################
# API Gateway (HTTP API)
############################

# HTTP API 생성
resource "aws_apigatewayv2_api" "admin_api" {
  name          = "${var.project_name}-admin-http-api"
  protocol_type = "HTTP"
}

# Lambda 통합
resource "aws_apigatewayv2_integration" "admin_integration" {
  api_id           = aws_apigatewayv2_api.admin_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.admin_lambda.arn
  integration_method = "POST"
}

# /admin/crawl/run-now POST
resource "aws_apigatewayv2_route" "run_now_route" {
  api_id    = aws_apigatewayv2_api.admin_api.id
  route_key = "POST /admin/crawl/run-now"
  target    = "integrations/${aws_apigatewayv2_integration.admin_integration.id}"
}

# /admin/schedule POST
resource "aws_apigatewayv2_route" "schedule_route" {
  api_id    = aws_apigatewayv2_api.admin_api.id
  route_key = "POST /admin/schedule"
  target    = "integrations/${aws_apigatewayv2_integration.admin_integration.id}"
}

# default stage
resource "aws_apigatewayv2_stage" "admin_stage" {
  api_id      = aws_apigatewayv2_api.admin_api.id
  name        = "$default"
  auto_deploy = true
}

# API Gateway가 admin Lambda 호출할 수 있도록 허용
resource "aws_lambda_permission" "allow_apigw_invoke_admin" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.admin_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.admin_api.execution_arn}/*/*"
}

############################
# Outputs
############################

output "admin_api_invoke_url" {
  description = "Base URL of the admin HTTP API (use POST /admin/crawl/run-now, POST /admin/schedule)"
  value       = aws_apigatewayv2_api.admin_api.api_endpoint
}

output "crawler_lambda_name" {
  description = "Name of the crawler Lambda function"
  value       = aws_lambda_function.crawler_lambda.function_name
}

output "admin_lambda_name" {
  description = "Name of the admin API Lambda function"
  value       = aws_lambda_function.admin_lambda.function_name
}

output "dynamodb_tables" {
  description = "DynamoDB tables used by the crawler"
  value = {
    source_state = aws_dynamodb_table.source_state.name
    documents    = aws_dynamodb_table.documents.name
    locks        = aws_dynamodb_table.locks.name
    schedule     = aws_dynamodb_table.schedule.name
  }
}