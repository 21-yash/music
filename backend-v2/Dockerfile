# Use Node.js LTS (Iron)
FROM node:20-alpine AS builder

# Create app directory
WORKDIR /app

# Install app dependencies
COPY package*.json ./
RUN npm ci

# Copy app source
COPY . .

# Build the TypeScript code
RUN npm run build

# Production image
FROM node:20-alpine

WORKDIR /app

# Copy package.json and install ONLY production dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy built files from builder
COPY --from=builder /app/dist ./dist

# Expose port (Fly.io defaults to 8080 internally, or we can use 3000)
ENV PORT=3000
EXPOSE 3000

# Start the application
CMD [ "npm", "start" ]
