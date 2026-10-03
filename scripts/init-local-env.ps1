param(
    [string]$Path = (Join-Path $PSScriptRoot "..\.env")
)

$ErrorActionPreference = "Stop"
$resolvedPath = [System.IO.Path]::GetFullPath($Path)

if (Test-Path -LiteralPath $resolvedPath) {
    throw "Refusing to overwrite existing environment file: $resolvedPath"
}

function New-RandomSecret([int]$bytes = 48) {
    $buffer = [byte[]]::new($bytes)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
    return [Convert]::ToHexString($buffer).ToLowerInvariant()
}

$lines = @(
    "SPRING_PROFILES_ACTIVE=local"
    "USER_DB_USERNAME=snabbit"
    "USER_DB_PASSWORD=$(New-RandomSecret 24)"
    "BOOKING_DB_USERNAME=snabbit"
    "BOOKING_DB_PASSWORD=$(New-RandomSecret 24)"
    "WORKER_DB_USERNAME=snabbit"
    "WORKER_DB_PASSWORD=$(New-RandomSecret 24)"
    "WALLET_DB_USERNAME=snabbit"
    "WALLET_DB_PASSWORD=$(New-RandomSecret 24)"
    "SUPPORT_DB_USERNAME=snabbit"
    "SUPPORT_DB_PASSWORD=$(New-RandomSecret 24)"
    "NOTIFICATION_DB_USERNAME=snabbit"
    "NOTIFICATION_DB_PASSWORD=$(New-RandomSecret 24)"
    "JWT_SECRET=$(New-RandomSecret)"
    "OTP_PEPPER=$(New-RandomSecret)"
    "SERVICE_AUTH_TOKEN=$(New-RandomSecret)"
    "RAZORPAY_MODE=fake"
    "RAZORPAY_KEY_ID="
    "RAZORPAY_KEY_SECRET="
    "RAZORPAY_WEBHOOK_SECRET=$(New-RandomSecret)"
)

[System.IO.File]::WriteAllLines($resolvedPath, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Output "Created $resolvedPath with random local-only credentials."
