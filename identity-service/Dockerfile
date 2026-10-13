FROM node:22-bookworm-slim AS build
WORKDIR /authority
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web ./web
COPY public ./public
RUN npm run build && npm prune --omit=dev

FROM node:22-bookworm-slim
ENV NODE_ENV=production
WORKDIR /authority
COPY --from=build --chown=node:node /authority/node_modules ./node_modules
COPY --from=build --chown=node:node /authority/public ./public
COPY --chown=node:node src ./src
COPY --chown=node:node package.json ./package.json
USER node
EXPOSE 8080
CMD ["node", "src/server.js"]
