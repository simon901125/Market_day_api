#!/usr/bin/env bash

# Ubuntu Spring Boot launcher used by marketday.service.
# The deployment workflow builds the JAR before restarting the service.

# Public application URL
export SWAGGER_PUBLIC_BASE_URL='https://api.marketday.dev'

# SQL Server settings
export DB_URL='jdbc:sqlserver://127.0.0.1:1433;databaseName=MarketDayDB;encrypt=true;trustServerCertificate=true'
export DB_USERNAME='sa'
export DB_PASSWORD='Simon901125'

# Google OAuth settings
export GOOGLE_CLIENT_ID='322007662830-udt5d156946h171o1edm93bj6pfu16cj.apps.googleusercontent.com'

# Gmail SMTP settings
export MAIL_USERNAME='simon901125@gmail.com'
export MAIL_PASSWORD='srdn gsqs aote ftzr'

# NewebPay settings
# MerchantID, HashKey, and HashIV are stored per organizer in the database.
# Remove any legacy process-level store credentials inherited by the service.
unset NEWEBPAY_MERCHANT_ID
unset NEWEBPAY_HASH_KEY
unset NEWEBPAY_HASH_IV

export PAYMENT_CREDENTIAL_ENCRYPTION_KEY='MarketDay-local-payment-secret-2026-at-least-32'
if [[ ${#PAYMENT_CREDENTIAL_ENCRYPTION_KEY} -lt 32 ]]; then
    echo "PAYMENT_CREDENTIAL_ENCRYPTION_KEY is required and must contain at least 32 characters." >&2
    exit 1
fi

export NEWEBPAY_NOTIFY_URL='https://api.marketday.dev/api/newebpay/notify'
export NEWEBPAY_RETURN_URL='https://api.marketday.dev/api/newebpay/return'
export NEWEBPAY_ORGANIZER_VERIFICATION_RETURN_URL='https://api.marketday.dev/api/newebpay/organizer-verification/return'

export FRONTEND_URL='https://market-day-web.pages.dev'

# Google Cloud Storage
export IMAGE_STORAGE='gcs'
export GCS_BUCKET='market_day_images'
export GCS_PUBLIC_BASE_URL='https://storage.googleapis.com'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${MARKETDAY_JAR_PATH:-${SCRIPT_DIR}/target/demo-0.0.1-SNAPSHOT.jar}"

if [[ ! -f "${JAR_PATH}" ]]; then
    echo "Spring Boot JAR not found: ${JAR_PATH}" >&2
    exit 1
fi

exec /usr/bin/java -jar "${JAR_PATH}"
