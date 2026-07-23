#!/usr/bin/env bash

# Ubuntu Spring Boot launcher used by marketday.service.
# The deployment workflow builds the JAR before restarting the service.

# Public application URL
export SWAGGER_PUBLIC_BASE_URL='https://api.marketday.dev'

# SQL Server settings
export DB_URL='jdbc:sqlserver://10.21.160.3:1433;databaseName=MarketDayDB;encrypt=true;trustServerCertificate=true'
export DB_USERNAME='sqlserver'
export DB_PASSWORD='Simon901125'

# Google OAuth settings
export GOOGLE_CLIENT_ID='322007662830-udt5d156946h171o1edm93bj6pfu16cj.apps.googleusercontent.com'

# Gmail SMTP settings
export MAIL_USERNAME='simon901125@gmail.com'
export MAIL_PASSWORD='srdn gsqs aote ftzr'

# NewebPay settings
export NEWEBPAY_MERCHANT_ID='MS159696944'
export NEWEBPAY_HASH_KEY='X4PbYeq4MAD5kNo6Ha7m5H1jl2R61bDx'
export NEWEBPAY_HASH_IV='CLy3UEghj63S3YKP'
export NEWEBPAY_NOTIFY_URL='https://api.marketday.dev/api/newebpay/notify'
export NEWEBPAY_RETURN_URL='https://api.marketday.dev/api/newebpay/return'
export FRONTEND_URL='https://market-day-1hp.pages.dev'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${MARKETDAY_JAR_PATH:-${SCRIPT_DIR}/target/demo-0.0.1-SNAPSHOT.jar}"

if [[ ! -f "${JAR_PATH}" ]]; then
    echo "Spring Boot JAR not found: ${JAR_PATH}" >&2
    exit 1
fi

exec /usr/bin/java -jar "${JAR_PATH}"
