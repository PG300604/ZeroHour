# 🚀 Deploying ZeroHour Backend to Render (Lifetime Free Tier)

This guide walks you through deploying the **ZeroHour** Spring Boot backend service to **Render** (`render.com`) for 100% lifetime free hosting.

---

## 📋 Prerequisites
1. A free account on [Render](https://render.com).
2. Your **ZeroHour** GitHub repository (`https://github.com/PG300604/ZeroHour.git`).
3. Your Google & Gemini API Credentials:
   - `GOOGLE_CLIENT_ID`
   - `GOOGLE_CLIENT_SECRET`
   - `GEMINI_API_KEY`
   - `FIREBASE_SERVICE_ACCOUNT_BASE64` (Base64 string of `firebase-service-account.json`)

---

## ⚙️ Step-by-Step Deployment Instructions

### Option A: Blueprint 1-Click Deployment (Recommended)

1. Log in to [Render Dashboard](https://dashboard.render.com).
2. Click **New +** -> Select **Blueprint**.
3. Connect your GitHub repository `PG300604/ZeroHour`.
4. Render will automatically detect [`render.yaml`](file:///p:/ZeroHour/render.yaml).
5. Name your Service (e.g. `zerohour-backend`).
6. Under **Environment Variables**, fill in the secrets:
   - `GOOGLE_CLIENT_ID`
   - `GOOGLE_CLIENT_SECRET`
   - `GEMINI_API_KEY`
   - `FIREBASE_SERVICE_ACCOUNT_BASE64`
7. Click **Apply**. Render will build the Docker container and deploy your backend!

---

### Option B: Manual Web Service Setup

1. Click **New +** -> **Web Service**.
2. Connect your GitHub repository `PG300604/ZeroHour`.
3. Configure the following fields:
   - **Name**: `zerohour-backend`
   - **Language / Environment**: `Docker`
   - **Dockerfile Path**: `./Dockerfile`
   - **Region**: `Singapore` (or preferred region)
   - **Instance Type**: `Free` ($0/month)
4. Add Environment Variables under **Advanced**:
   - `PORT` = `10000`
   - `SPRING_PROFILES_ACTIVE` = `prod`
   - `GOOGLE_CLIENT_ID` = `your-google-client-id`
   - `GOOGLE_CLIENT_SECRET` = `your-google-client-secret`
   - `GEMINI_API_KEY` = `your-gemini-key`
   - `FIREBASE_PROJECT_ID` = `zerohour-a84d3`
   - `FIREBASE_SERVICE_ACCOUNT_BASE64` = `your-base64-firebase-json`
   - `FRONTEND_URL` = `https://zerohour-a84d3.web.app`
   - `CORS_ALLOWED_ORIGINS` = `http://localhost:5173,https://zerohour-a84d3.web.app,https://zerohour-a84d3.firebaseapp.com`
5. Click **Create Web Service**.

---

## 🌐 Post-Deployment Checklist

1. Copy your new Render Service URL (e.g., `https://zerohour-backend.onrender.com`).
2. Add your Render backend URL to **Google Cloud Console**:
   - OAuth 2.0 Client -> Authorized Redirect URIs:
     `https://zerohour-backend.onrender.com/login/oauth2/code/google`
3. Add your Render backend URL to **Firebase Console**:
   - Authentication -> Settings -> Authorized Domains:
     `zerohour-backend.onrender.com`
