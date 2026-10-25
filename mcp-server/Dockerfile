FROM node:22-bookworm-slim AS build
WORKDIR /srv/mcp
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:22-bookworm-slim
ENV NODE_ENV=production
WORKDIR /srv/mcp
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY --from=build /srv/mcp/dist ./dist
COPY public ./public
RUN mkdir -p /var/lib/gws-mcp && chown -R node:node /srv/mcp /var/lib/gws-mcp
USER node
EXPOSE 3000
CMD ["npm", "start"]
