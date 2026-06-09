FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .

ARG VITE_ENV=production
ARG VITE_API_BASE_URL
ARG VITE_YANDEX_MAPS_API_KEY=
ARG VITE_DGIS_API_KEY=
ENV VITE_ENV=$VITE_ENV
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_YANDEX_MAPS_API_KEY=$VITE_YANDEX_MAPS_API_KEY
ENV VITE_DGIS_API_KEY=$VITE_DGIS_API_KEY

RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY default.conf.template /etc/nginx/templates/default.conf.template

ENV DOMAIN=example.com
ENV GATEWAY_HOST=gateway
ENV NGINX_ENVSUBST_FILTER="^(DOMAIN|GATEWAY_HOST)$$"

EXPOSE 80 443
CMD ["nginx", "-g", "daemon off;"]
