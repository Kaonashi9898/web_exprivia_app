FROM node:24-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

EXPOSE 4201

# v05: l'editor viene servito sotto /editor/ per poter essere pubblicato
# dietro Apache sullo stesso virtual host del frontend principale.
CMD ["npm", "run", "start", "--", "--host", "0.0.0.0", "--port", "4201", "--serve-path", "/editor/"]
