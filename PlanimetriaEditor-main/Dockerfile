FROM node:24-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

EXPOSE 4201

CMD ["npm", "run", "start", "--", "--host", "0.0.0.0"]
