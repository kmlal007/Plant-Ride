# Browser build of the mobile app, used for demos and desktop access.
# Production phones use native builds (EAS / Android Studio), not this image.
# Build context: repository root (see docker-compose.yml).
FROM node:22-alpine AS build
WORKDIR /app
COPY driver-app/package.json driver-app/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY driver-app/ ./
ENV EXPO_OFFLINE=1 CI=1
RUN npx expo export --platform web --output-dir dist

FROM nginx:1.27-alpine
ENV BACKEND_URL=http://backend:8080
COPY deploy/nginx/spa.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
