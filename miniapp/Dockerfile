# ---- build ----
FROM node:22-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=""
ARG VITE_BOT_USERNAME=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_BOT_USERNAME=$VITE_BOT_USERNAME
RUN npm run build

# ---- serve ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
