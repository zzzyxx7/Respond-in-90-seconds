# Docker 部署说明

## 1. 服务器准备

- 安装好 Docker 与 Docker Compose（你选 `Ubuntu22.04-Docker26` 镜像即可）
- 防火墙至少放行：`22`、`80`、`8081`、`8000`
- 如需访问 RabbitMQ 管理台，再放行 `15672`

## 2. 拉代码

```bash
git clone <your-repo-url> doc-fusion
cd doc-fusion
```

## 3. 配置 AI 环境变量

```bash
cp .env.prod.example .env
```

把 `.env` 里的 `A23_DEEPSEEK_API_KEY` 改成你自己的真实密钥。

## 4. 启动

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

## 5. 访问地址

- 前端：`http://49.235.168.172/combined_final.html`
- 后端 Swagger：`http://49.235.168.172:8081/swagger-ui/index.html`
- A23 文档：`http://49.235.168.172:8000/docs`
- RabbitMQ 管理台：`http://49.235.168.172:15672`

## 6. 常用命令

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f a23
docker compose -f docker-compose.prod.yml down
```
