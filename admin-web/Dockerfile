# Build context: repository root (see docker-compose.yml).
FROM node:22-alpine AS build
WORKDIR /app
COPY admin-web/package.json admin-web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY admin-web/ ./
# Map tiles; point at a self-hosted tile server for confidential plant layouts.
ARG VITE_TILE_URL=
ENV VITE_TILE_URL=$VITE_TILE_URL
RUN npm run build

FROM nginx:1.27-alpine
ENV BACKEND_URL=http://backend:8080
COPY deploy/nginx/spa.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
