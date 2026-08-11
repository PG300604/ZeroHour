# ⏰ ZeroHour — Multi-Agent AI Productivity Companion

**ZeroHour** is a premium, multi-agent AI task management and crisis-response companion built for the **Vibe2Ship Hackathon (CodingNinjasXGoogle)**. It helps professionals, students, and developers navigate tight deadlines, manage task priorities, sync with Google Calendar, and resolve time-crunches through an interactive, multimodal **Panic Mode**.

---

## 🚀 Live Production Links
* 🌐 **Deployable Link (Hosted on Firebase Hosting)**: [https://zerohour-a84d3.web.app](https://zerohour-a84d3.web.app)
* ⚙️ **Backend API (Hosted on Render - Lifetime Free)**: [`https://zerohour-backend-e4sl.onrender.com`](https://zerohour-backend-e4sl.onrender.com)
* 📖 **Render Deployment Guide**: See [RENDER_DEPLOY.md](RENDER_DEPLOY.md)

---

## 🧠 Core Architecture: The 4-Agent Pipeline

ZeroHour orchestrates a pipeline of four specialized AI agents to automate task management:

```mermaid
graph TD
    User([User Task Input]) --> Planner[1. PlannerAgent]
    Planner -- Generates Subtasks --> Prioritizer[2. PrioritizerAgent]
    Prioritizer -- Sorts & Reasons Priorities --> Scheduler[3. SchedulerAgent]
    Scheduler -- Syncs to Google Calendar --> Nudge[4. NudgeAgent]
    Nudge -- Triggers Email Nudges --> Gmail[Gmail API]
```

1. **PlannerAgent**: Breaks down complex tasks and deadlines into bite-sized, actionable subtasks with estimated durations.
2. **PrioritizerAgent**: Analyzes the subtasks alongside the user's workload to dynamically assign logical execution priorities.
3. **SchedulerAgent**: Evaluates the user's calendar constraints and automatically creates calendar slots via the **Google Calendar API**.
4. **NudgeAgent**: Monitors upcoming deadlines in real-time and sends customized email alerts via the **Gmail API** to prevent procrastination.

---

## 🚨 Panic Mode: Crisis Management Flow
When users face a sudden crunch, **Panic Mode** acts as their virtual Chief of Staff:
* **Multimodal Attachment Analysis**: Upload syllabi, PDFs, or photos of assignments. ZeroHour's multimodal Gemini engine processes the content to extract milestones and deadlines instantly.
* **Gemini-Style History Sidebar**: Manage past crisis conversations. Easily create new chats, reload history, rename sessions inline, or delete logs.
* **Real-Time Agent Streams**: Watch the agents think, plan, and schedule live through Server-Sent Events (SSE).
* **Task Crisis Overwrite**: Trigger Panic Mode on an existing task to overwrite its subtasks and calendar events automatically rather than creating duplicates.

---

## 🆕 Pacing, Personalization & CRUD Customization
We introduced deep customization and flexible calendar pacing:
* **30-Day Session Persistence**: Hardened session cookie max-age to 30 days so users remain logged in across browser sessions.
* **Custom Work/Focus Hours**: Define custom sleep/wake hours on the Settings page to restrict scheduling to your active window.
* **Smart Calendar Pacing & Breaks**: Spacers distribute task blocks evenly over days, inject **10-minute breaks** between consecutive blocks, and shift late-night tasks to the next morning.
* **Two-Way Calendar Pull-Sync**: Click the "Sync Calendar" button on any task details page to fetch latest titles and timings directly from Google Calendar.
* **Manual Subtask CRUD**: Add, edit, or delete plan milestones manually with automatic parent task status and progress recalculations.
* **Mobile Bottom Navigation Bar**: Native bottom navigation bar for mobile screens, giving quick access to Core, Calendar, Panic, Settings, and Exit controls.

---

## 🛡️ Security Hardening & Infrastructure
* **Render Blueprint Infrastructure**: Configured 1-click Docker deployment via [`render.yaml`](render.yaml) for 100% lifetime free backend hosting.
* **Reverse Proxy SSL Support**: Enabled `server.forward-headers-strategy=framework` to seamlessly process Render's `X-Forwarded-Proto` HTTPS headers and preserve OAuth session cookies across redirects.
* **PKCS#8 RSA Key Sanitization**: Robust Base64 decoding parser in `FirebaseConfig.java` to handle raw JSON strings and unescape literal newlines (`\n`) in service account private keys.
* **CORS/Adblock Immune Brand Logos**: Logos for Gemini, Calendar, Firebase, Drive, Gmail, and Meet are embedded as inline vector SVGs to prevent browser-level network blocking.
* **50MB Request Payload Ingestion**: Configured Tomcat and Spring servlet parameters to accept up to 50MB PDF/image syllabus uploads.
* **Automatic Fallback API Resolution**: Integrated automatic Render production URL API fallback in the frontend `api.js` to ensure production calls resolve correctly regardless of compilation environment variables.
* **Public Legal Routes**: Registered public React Router paths `/about`, `/privacy`, `/terms`, and `/security` with matching WebSecurity customizers.
* **CSRF Protection**: Spring Security generates double-submitted CSRF tokens stored in a secure cookie (`XSRF-TOKEN`) and validated on all mutating requests.

---

## 💻 Local Development Setup

### Automated PowerShell Script (Windows)
Run the convenience startup script to set PATH variables (Java 17, Maven, Node) and launch both backend and frontend servers:
```powershell
.\run_dev.ps1
```

### Manual Setup

#### Backend (Spring Boot)
1. Navigate to the backend folder:
   ```bash
   cd backend
   ```
2. Configure your environment variables in `.env` (or copy from `backend/.env.template`):
   ```properties
   GOOGLE_CLIENT_ID=your-client-id
   GOOGLE_CLIENT_SECRET=your-client-secret
   GEMINI_API_KEY=your-gemini-key
   ```
3. Run the backend:
   ```bash
   mvn spring-boot:run
   ```

#### Frontend (React)
1. Navigate to the frontend folder:
   ```bash
   cd frontend
   ```
2. Install dependencies and start the Vite dev server:
   ```bash
   npm install
   npm run dev
   ```
