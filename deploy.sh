#!/bin/bash
set -e

# ── Config ────────────────────────────────────────────────────────────────────
PROJECT_ID="your-gcp-project-id"
REGION="asia-south1"
SERVICE_NAME="zerohour"
IMAGE_NAME="gcr.io/$PROJECT_ID/$SERVICE_NAME"

echo "🚀 ZeroHour Cloud Run Deploy"
echo "Project: $PROJECT_ID | Region: $REGION"

# ── Step 1: Build frontend and copy to Spring Boot static resources ───────────
echo "📦 Building React frontend..."
cd frontend
npm run build
cd ..

# Copy React build output to Spring Boot static resources
echo "📁 Copying frontend build to backend static resources..."
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -r frontend/dist/* backend/src/main/resources/static/

# ── Step 2: Build Docker image ────────────────────────────────────────────────
echo "🐳 Building Docker image..."
docker build -t $IMAGE_NAME .

# ── Step 3: Push to Google Container Registry ─────────────────────────────────
echo "📤 Pushing image to GCR..."
docker push $IMAGE_NAME

# ── Step 4: Deploy to Cloud Run ───────────────────────────────────────────────
echo "☁️  Deploying to Cloud Run..."
gcloud run deploy $SERVICE_NAME \
  --image $IMAGE_NAME \
  --platform managed \
  --region $REGION \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 3 \
  --timeout 300 \
  --set-env-vars "\
GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID,\
GOOGLE_CLIENT_SECRET=$GOOGLE_CLIENT_SECRET,\
GEMINI_API_KEY=$GEMINI_API_KEY,\
FIREBASE_PROJECT_ID=$FIREBASE_PROJECT_ID,\
FIREBASE_SERVICE_ACCOUNT_BASE64=$FIREBASE_SERVICE_ACCOUNT_BASE64,\
AES_SECRET_KEY=$AES_SECRET_KEY,\
FRONTEND_URL=https://$SERVICE_NAME-$PROJECT_ID.run.app,\
SPRING_PROFILES_ACTIVE=prod"

# ── Step 5: Get deployed URL ───────────────────────────────────────────────────
DEPLOYED_URL=$(gcloud run services describe $SERVICE_NAME \
  --region $REGION \
  --format 'value(status.url)')

echo ""
echo "✅ ZeroHour deployed successfully!"
echo "🌐 URL: $DEPLOYED_URL"
echo ""
echo "Next steps:"
echo "1. Add $DEPLOYED_URL to Google OAuth authorized redirect URIs"
echo "2. Add $DEPLOYED_URL to Firebase authorized domains"
echo "3. Update FRONTEND_URL env var if using separate frontend hosting"
