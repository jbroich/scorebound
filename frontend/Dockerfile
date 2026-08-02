# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS build
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY . ./
ARG VITE_SCOREBOUND_TIME_ZONE=Europe/Berlin
ENV VITE_SCOREBOUND_TIME_ZONE=${VITE_SCOREBOUND_TIME_ZONE}
RUN npm run build

FROM nginx:1.28-alpine AS runtime
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/dist/ /usr/share/nginx/html/

USER nginx
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
