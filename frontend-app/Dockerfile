# syntax=docker/dockerfile:1

# Stage 1: Build Angular application
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies using clean install
COPY package*.json ./
RUN npm ci

# Copy project files and build production distribution
COPY . .
RUN npm run build

# Stage 2: Serve using Nginx
FROM nginx:alpine AS runner

# Remove default nginx static assets
RUN rm -rf /usr/share/nginx/html/*

# Copy build artifacts and custom nginx configuration
COPY --from=build /app/dist/frontend-app/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
