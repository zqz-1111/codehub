param(
    [string]$RuntimeDirectory = (Join-Path $PSScriptRoot '../.local-runtime')
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$java = 'E:/jdk-21.0.7+6/bin/java.exe'
$node = 'F:/node.js/install/node.exe'
$jar = Join-Path $projectRoot 'codehub-server/target/codehub-server-1.0.0.jar'
$vite = Join-Path $projectRoot 'codehub-web/node_modules/vite/bin/vite.js'
foreach ($file in @($java,$node,$jar,$vite)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "缺少本地运行文件: $file" }
}
& docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw '请先打开Docker Desktop并等待引擎就绪。' }
foreach ($container in @('codehub-mysql','codehub-redis','codehub-minio','codehub-rabbitmq')) {
    & docker start $container
    if ($LASTEXITCODE -ne 0) { throw "启动已有容器失败: $container；本脚本不会重建数据卷。" }
}
# Existing deployments must already have the additive schema upgrade.
$schemaQuery = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='codehub' AND table_name='upload_parts' AND column_name='size_bytes'; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='codehub' AND table_name='upload_sessions';"
$mysqlReady = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    $schema = $schemaQuery | & docker exec -i codehub-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names' 2>$null
    if ($LASTEXITCODE -eq 0) { $mysqlReady=$true; break }
    Start-Sleep -Seconds 1
}
if (-not $mysqlReady) { throw 'MySQL尚未就绪，请检查容器日志。' }
if (@($schema).Count -ne 2 -or @($schema)[0] -ne '1' -or @($schema)[1] -ne '1') {
    throw '数据库结构尚未升级，请先按分片升级文档备份、预检并执行迁移。'
}
$vhosts = & docker exec codehub-rabbitmq rabbitmqctl list_vhosts -q name
if ($LASTEXITCODE -ne 0) { throw 'RabbitMQ尚未就绪，稍后重试。' }
if ('codehub-validation' -notin @($vhosts)) {
    & docker exec codehub-rabbitmq rabbitmqctl add_vhost codehub-validation
    if ($LASTEXITCODE -ne 0) { throw '创建本地验证vhost失败。' }
    & docker exec codehub-rabbitmq rabbitmqctl set_permissions -p codehub-validation guest '.*' '.*' '.*'
    if ($LASTEXITCODE -ne 0) { throw '设置本地验证vhost权限失败。' }
}
New-Item -ItemType Directory -Force -Path $RuntimeDirectory | Out-Null
$processes = @{}
if (-not (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) {
    # Explicit placeholders prevent accidental paid inference from inherited environment settings.
    $backend = Start-Process -FilePath $java -ArgumentList @('-jar',$jar,
        '--server.address=127.0.0.1','--server.port=8080',
        '--spring.rabbitmq.virtual-host=codehub-validation',
        '--llm.base-url=http://127.0.0.1:9','--llm.api-key=sk-placeholder',
        '--llm.embedding-key=sk-placeholder','--logging.level.com.codehub=INFO') `
        -WorkingDirectory (Join-Path $projectRoot 'codehub-server') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $RuntimeDirectory 'backend.log') `
        -RedirectStandardError (Join-Path $RuntimeDirectory 'backend.err.log')
    $processes.backend_pid = $backend.Id
} else { Write-Output '8080已有监听进程，保留现有进程。' }
if (-not (Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue)) {
    $frontend = Start-Process -FilePath $node -ArgumentList @($vite,'--host','127.0.0.1','--port','5173','--strictPort') `
        -WorkingDirectory (Join-Path $projectRoot 'codehub-web') -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $RuntimeDirectory 'frontend.log') `
        -RedirectStandardError (Join-Path $RuntimeDirectory 'frontend.err.log')
    $processes.frontend_pid = $frontend.Id
} else { Write-Output '5173已有监听进程，保留现有进程。' }
$processes | ConvertTo-Json | Set-Content (Join-Path $RuntimeDirectory 'processes-started-this-run.json') -Encoding utf8
Write-Output '本地前端：http://127.0.0.1:5173；后端：http://127.0.0.1:8080。'
Write-Output '本脚本启动的新后端禁用真实LLM/Embedding；已有端口进程不会被替换或修改配置。'
