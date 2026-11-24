# Imagem do front do Financeiro, servida sob /modulos/financeiro/ (Contrato §12.8, porta §13.3).
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
ENV PORTA=3005
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html/modulos/financeiro
EXPOSE 3005
