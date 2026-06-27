import { useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { api } from '../services/api';
import ScrollBackground from '../components/landing/ScrollBackground';
import { GoogleIcon } from '../components/landing/GoogleButton';
import { GeminiLogo, CalendarLogo, FirebaseLogo, DriveLogo, GmailLogo, MeetLogo } from '../components/BrandLogos';

export default function Landing() {
  const navigate = useNavigate();

  // Set document title
  useEffect(() => {
    document.title = 'ZeroHour — Your AI Deadline Companion';
  }, []);

  // If already logged in, redirect to dashboard
  useEffect(() => {
    const checkLogin = async () => {
      try {
        await api.getMe();
        navigate('/dashboard');
      } catch {
        // Not logged in, stay on landing page
      }
    };
    checkLogin();
  }, [navigate]);

  // Intersection Observer for scroll reveal animations
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('revealed');
          }
        });
      },
      { threshold: 0.05 }
    );

    const targets = document.querySelectorAll('.reveal-on-scroll');
    targets.forEach((el) => {
      observer.observe(el);
    });

    return () => {
      targets.forEach((el) => observer.unobserve(el));
    };
  }, []);

  return (
    <div className="font-body-md text-body-md selection:bg-primary selection:text-on-primary-container text-on-surface min-h-screen relative overflow-hidden animate-fadeIn">
      <div className="grain-overlay"></div>
      
      {/* Scroll-Linked Day-Night Cycle Background */}
      <ScrollBackground />
      
      <header className="fixed top-0 w-full z-50 bg-transparent backdrop-blur-md border-b border-subtle">
        <div className="flex justify-between items-center px-6 md:px-margin-desktop py-md mx-auto w-full max-w-7xl">
          <div className="text-[28px] md:text-[46px] font-black text-on-surface tracking-tighter leading-none font-headline-lg select-none">ZeroHour</div>
          <nav className="hidden md:flex items-center gap-xl">
            <a className="text-on-surface-variant font-medium hover:text-primary transition-colors duration-200" href="#network">Network</a>
            <a className="text-on-surface-variant font-medium hover:text-primary transition-colors duration-200" href="#panic">Panic Mode</a>
            <a className="text-on-surface-variant font-medium hover:text-primary transition-colors duration-200" href="#how-it-works">How it Works</a>
            <a className="text-on-surface-variant font-medium hover:text-primary transition-colors duration-200" href="#ecosystem">Integrations</a>
          </nav>
          <a 
            href={api.loginUrl}
            className="bg-[#ffb4ac] text-[#690006] hover:bg-[#ffa097] font-extrabold px-4 md:px-8 py-2 md:py-3 rounded-lg active:scale-95 transition-all text-xs md:text-sm uppercase tracking-wide flex items-center justify-center gap-2 shadow-md shadow-[#ffb4ac]/35"
          >
            <GoogleIcon size={16} />
            Get Started
          </a>
        </div>
      </header>

      <main>
        {/* Hero Section */}
        <section className="relative min-h-screen flex flex-col items-center justify-center pt-xl overflow-hidden bg-transparent">
          {/* Background Video */}
          <video 
            autoPlay 
            loop 
            muted 
            playsInline 
            className="absolute inset-0 w-full h-full object-cover opacity-40 z-0 pointer-events-none"
          >
            <source src="https://player.vimeo.com/external/494252666.hd.mp4?s=2f059ef89583ccfe39ca0040f92f6c04f7c22971&amp;profile_id=175" type="video/mp4" />
          </video>
          {/* Background Overlay to ensure readability */}
          <div className="absolute inset-0 bg-background/20 z-[1] pointer-events-none"></div>
          
          {/* Pulsing Portal Background */}
          <div className="absolute inset-0 z-[2] flex items-center justify-center pointer-events-none">
            <div className="w-[800px] h-[800px] rounded-full pulse-glow-red" style={{ background: 'radial-gradient(circle, rgba(255, 69, 58, 0.15) 0%, transparent 70%)' }} />
          </div>
          
          {/* Hero Content */}
          <div className="relative z-10 text-center px-margin-mobile">
            <div className="mb-lg opacity-80 uppercase tracking-[0.4em] font-label-caps text-label-caps text-primary">Mission Critical AI</div>
            <h1 className="text-[44px] md:text-[80px] font-black leading-none tracking-tight mb-md drop-shadow-2xl uppercase italic">
              Your deadline<br />
              <span className="text-outline">just met its</span> <span className="text-[#E53935]">match.</span>
            </h1>
            <p className="mx-auto text-on-surface-variant font-body-lg text-body-lg mb-xl drop-shadow-lg max-w-2xl">
              The command center for your high-stakes productivity. Autonomous agents that anticipate bottlenecks before they become catastrophes.
            </p>
            <div className="flex justify-center items-center">
              <a 
                href={api.loginUrl} 
                className="bg-[#ffb4ac] text-[#690006] hover:bg-[#ffa097] font-extrabold px-12 py-4 rounded-lg text-base shadow-lg shadow-[#ffb4ac]/35 active:scale-95 transition-all inline-flex items-center justify-center gap-3"
              >
                <GoogleIcon size={20} />
                Launch Operations
              </a>
            </div>
          </div>
        </section>

        {/* Panic Mode Showcase */}
        <section id="panic" className="py-24 px-margin-desktop mx-auto max-w-7xl w-full">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-xl items-center w-full">
            <div className="space-y-md w-full">
              <div className="flex items-center gap-sm">
                <span className="w-3 h-3 bg-error rounded-full animate-pulse"></span>
                <span className="font-label-caps text-label-caps text-error">PANIC MODE ENABLED</span>
              </div>
              <h2 className="font-headline-lg text-headline-lg reveal-on-scroll">When panic sets in,<br />ZeroHour takes over.</h2>
              <p className="text-on-surface-variant font-body-lg text-body-lg max-w-lg">
                Detected an impossible delivery timeline? ZeroHour shifts into ultra-high-density mode, automating secondary tasks and focusing your entire agent network on the critical path.
              </p>
              <div className="grid grid-cols-2 gap-md pt-md">
                <div className="glass-card p-md reveal-on-scroll">
                  <div className="text-primary font-bold text-headline-md mb-xs font-mono">0.4s</div>
                  <div className="text-label-caps text-on-surface-variant opacity-70">Response Time</div>
                </div>
                <div className="glass-card p-md reveal-on-scroll">
                  <div className="text-primary font-bold text-headline-md mb-xs font-mono">Auto</div>
                  <div className="text-label-caps text-on-surface-variant opacity-70">Conflict Resolution</div>
                </div>
              </div>
            </div>

            <div className="relative group w-full">
              <div className="absolute -inset-4 bg-error/10 blur-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-700"></div>
              <div className="glass-card glow-red overflow-hidden relative reveal-on-scroll">
                <div className="bg-surface-container-highest/50 px-md py-sm border-b border-subtle flex items-center justify-between">
                  <div className="flex gap-xs">
                    <div className="w-3 h-3 rounded-full bg-error/40"></div>
                    <div className="w-3 h-3 rounded-full bg-warning-amber/40"></div>
                    <div className="w-3 h-3 rounded-full bg-success-teal/40"></div>
                  </div>
                  <div className="font-label-caps text-[10px] tracking-widest text-on-surface-variant">AGENT_TERMINAL_V4</div>
                </div>
                <div className="p-lg space-y-md font-mono text-xs custom-scrollbar h-[350px] overflow-y-auto">
                  <div className="flex gap-md">
                    <span className="text-primary">[08:42]</span>
                    <span className="text-on-surface-variant">System: Critical bottleneck detected in "Q3 Report Delivery".</span>
                  </div>
                  <div className="flex gap-md">
                    <span className="text-primary">[08:42]</span>
                    <span className="text-on-surface">Agent: Initializing Panic Mode. Rearranging Calendar for maximum focus blocks.</span>
                  </div>
                  <div className="flex gap-md">
                    <span className="text-primary">[08:43]</span>
                    <span className="text-on-surface">Agent: Notified stakeholders of high-priority status. Drafting first iteration of Project Summary...</span>
                  </div>
                  <div className="flex gap-md">
                    <span className="text-primary">[08:43]</span>
                    <span className="text-success-teal">Success: Priority path cleared. Estimated completion: 14:00.</span>
                  </div>
                  
                  <div className="mt-xl p-md border border-error/30 bg-error/5 rounded-lg flex items-center justify-between">
                    <div className="flex flex-col">
                      <span className="text-error font-bold font-label-caps">PANIC RESOLUTION</span>
                      <span className="text-[12px] opacity-70 italic">Commanding Gemini Agent for draft generation...</span>
                    </div>
                    <div className="w-8 h-8 rounded-full border-2 border-error border-t-transparent animate-spin"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Agent Network */}
        <section id="network" className="py-24 bg-[#141416]/40 relative overflow-hidden">
          <div className="absolute inset-0 pointer-events-none opacity-20">
            <div className="absolute top-1/2 left-0 w-full h-px bg-gradient-to-r from-transparent via-primary to-transparent line-connection"></div>
            <div className="absolute top-1/4 left-1/4 w-full h-px rotate-12 bg-gradient-to-r from-transparent via-tertiary to-transparent line-connection" style={{ animationDelay: '1s' }}></div>
          </div>
          <div className="px-margin-desktop mx-auto relative z-10 w-full max-w-7xl">
            <div className="text-center mb-24">
              <h2 className="font-headline-lg text-headline-lg mb-md reveal-on-scroll">The Distributed Neural Core</h2>
              <p className="text-on-surface-variant mx-auto max-w-2xl">Four specialized entities working in perfect orchestration to protect your time.</p>
            </div>
            
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-lg w-full">
              {/* Planner Node */}
              <div className="agent-node glass-card p-lg border-primary/20 reveal-on-scroll w-full">
                <div className="w-12 h-12 rounded-lg bg-primary/10 flex items-center justify-center mb-md">
                  <span className="material-symbols-outlined text-primary text-2xl">architecture</span>
                </div>
                <h3 className="font-headline-md text-headline-md mb-sm">The Planner</h3>
                <p className="text-on-surface-variant text-body-md opacity-80">Deconstructs complex goals into actionable tactical steps.</p>
              </div>

              {/* Prioritizer Node */}
              <div className="agent-node glass-card p-lg border-error/20 reveal-on-scroll w-full">
                <div className="w-12 h-12 rounded-lg bg-error/10 flex items-center justify-center mb-md">
                  <span className="material-symbols-outlined text-error text-2xl">priority_high</span>
                </div>
                <h3 className="font-headline-md text-headline-md mb-sm">The Prioritizer</h3>
                <p className="text-on-surface-variant text-body-md opacity-80">Ranks every action based on mission impact and urgency.</p>
              </div>

              {/* Scheduler Node */}
              <div className="agent-node glass-card p-lg border-tertiary/20 reveal-on-scroll w-full">
                <div className="w-12 h-12 rounded-lg bg-tertiary/10 flex items-center justify-center mb-md">
                  <span className="material-symbols-outlined text-tertiary text-2xl">calendar_today</span>
                </div>
                <h3 className="font-headline-md text-headline-md mb-sm">The Scheduler</h3>
                <p className="text-on-surface-variant text-body-md opacity-80">Bends time by optimizing your calendar for deep work.</p>
              </div>

              {/* Nudge Node */}
              <div className="agent-node glass-card p-lg border-warning-amber/20 reveal-on-scroll w-full">
                <div className="w-12 h-12 rounded-lg bg-warning-amber/10 flex items-center justify-center mb-md">
                  <span className="material-symbols-outlined text-warning-amber text-2xl">notifications_active</span>
                </div>
                <h3 className="font-headline-md text-headline-md mb-sm">The Nudge</h3>
                <p className="text-on-surface-variant text-body-md opacity-80">Gentle context-aware prompts to keep you in flow state.</p>
              </div>
            </div>
          </div>
        </section>

        {/* Google Ecosystem Grid */}
        <section id="ecosystem" className="py-24 px-margin-desktop mx-auto w-full max-w-7xl">
          <div className="flex flex-col md:flex-row justify-between items-end mb-16 gap-md w-full">
            <div className="flex-1 w-full max-w-lg">
              <h2 className="font-headline-lg text-headline-lg mb-md reveal-on-scroll">Deep Ecosystem Integration</h2>
              <p className="text-on-surface-variant">ZeroHour lives where you work, tapping directly into your Google Cloud and Workspace environment.</p>
            </div>
            <button 
              onClick={() => window.open(api.loginUrl, '_self')}
              className="text-primary font-bold flex items-center gap-xs hover:underline"
            >
              View all 50+ integrations <span className="material-symbols-outlined">arrow_forward</span>
            </button>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-3 gap-md w-full">
            {/* Gemini */}
            <div className="glass-card p-lg flex flex-col items-center justify-center text-center group cursor-pointer hover:bg-surface-200 transition-colors reveal-on-scroll">
              <div className="w-10 h-10 mb-md opacity-60 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                <GeminiLogo />
              </div>
              <span className="font-label-caps text-label-caps">Gemini 1.5 Pro</span>
            </div>

            {/* Calendar */}
            <div className="glass-card p-lg flex flex-col items-center justify-center text-center group cursor-pointer hover:bg-surface-200 transition-colors reveal-on-scroll">
              <div className="w-10 h-10 mb-md opacity-60 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                <CalendarLogo />
              </div>
              <span className="font-label-caps text-label-caps">Workspace</span>
            </div>

            {/* Firebase */}
            <div className="glass-card p-lg flex flex-col items-center justify-center text-center group cursor-pointer hover:bg-surface-200 transition-colors reveal-on-scroll">
              <div className="w-10 h-10 mb-md opacity-60 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                <FirebaseLogo />
              </div>
              <span className="font-label-caps text-label-caps">Firebase</span>
            </div>

            {/* Drive */}
            <div className="glass-card p-lg flex flex-col items-center justify-center text-center group cursor-pointer hover:bg-surface-200 transition-colors reveal-on-scroll">
              <div className="w-10 h-10 mb-md opacity-60 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                <DriveLogo />
              </div>
              <span className="font-label-caps text-label-caps">Drive Storage</span>
            </div>

            {/* Gmail */}
            <div className="glass-card p-lg flex flex-col items-center justify-center text-center group cursor-pointer hover:bg-surface-200 transition-colors reveal-on-scroll">
              <div className="w-10 h-10 mb-md opacity-60 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                <GmailLogo />
              </div>
              <span className="font-label-caps text-label-caps">Gmail Agent</span>
            </div>

            {/* Meet */}
            <div className="glass-card p-lg flex flex-col items-center justify-center text-center group cursor-pointer hover:bg-surface-200 transition-colors reveal-on-scroll">
              <div className="w-10 h-10 mb-md opacity-60 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                <MeetLogo />
              </div>
              <span className="font-label-caps text-label-caps">Meet Scribe</span>
            </div>
          </div>
        </section>

        {/* How it Works */}
        <section id="how-it-works" className="py-24 px-margin-desktop bg-[#141416]/50 w-full">
          <div className="mx-auto w-full max-w-7xl">
            <div className="mb-24 text-center">
              <h2 className="font-headline-lg text-headline-lg reveal-on-scroll">Operations Protocol</h2>
            </div>
            
            <div className="space-y-32 w-full">
              {/* Step 1 */}
              <div className="flex flex-col md:flex-row items-center gap-xl w-full">
                <div className="flex-1 reveal-on-scroll">
                  <span className="font-display-countdown text-[120px] text-primary/40 leading-none font-mono">01</span>
                  <h3 className="font-headline-lg text-headline-lg mb-md">Sync the Horizon</h3>
                  <p className="text-on-surface-variant font-body-lg text-body-lg" style={{ maxWidth: '448px' }}>
                    Connect your ecosystem. ZeroHour scans your upcoming 30 days to build a temporal map of your obligations.
                  </p>
                </div>
                <div className="flex-1 w-full">
                  <div className="glass-card h-[400px] overflow-hidden relative glow-red flex items-center justify-center reveal-on-scroll">
                    <img alt="Sync Timeline" className="w-full h-full object-cover opacity-50" src="https://lh3.googleusercontent.com/aida-public/AB6AXuDmqYyM1MxhdsjN1gwb7WukXlxcWOXX1GjcxZ5FEk-sIxgbaKSJX9eL2mwTD2DQXooyNif8pMfey6YxY-3z2U8cJyeHRCvsmIil_CTdhk---D8ywbH18lImuXGMSrIQxR4rEVAKi8IsTQ_WKRYQjcssEoM4g6BoAJao0rgDfN8ex4THoIR-30YwC1MURlXArPqtF2nUmJsWriS1e3ExPSFvGi5rbD_yg_RsZhpHkDNdb4wLYqBqNOP55EBaNGX54TSy3P2X9wH2nwQ" />
                    <div className="absolute inset-0 bg-gradient-to-t from-background to-transparent"></div>
                    <div className="absolute bottom-lg left-lg right-lg p-md glass-card flex items-center gap-md">
                      <span className="material-symbols-outlined text-primary animate-spin">sync</span>
                      <span className="font-label-caps text-label-caps text-xs">MAPPING 43 EVENTS...</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Step 2 */}
              <div className="flex flex-col md:flex-row-reverse items-center gap-xl w-full">
                <div className="flex-1 reveal-on-scroll">
                  <span className="font-display-countdown text-[120px] text-success-teal/40 leading-none font-mono">02</span>
                  <h3 className="font-headline-lg text-headline-lg mb-md">Deploy Agents</h3>
                  <p className="text-on-surface-variant font-body-lg text-body-lg" style={{ maxWidth: '448px' }}>
                    Specialized agents begin working in the background—cleaning your inbox, rescheduling low-value meetings, and preparing draft materials.
                  </p>
                </div>
                <div className="flex-1 w-full">
                  <div className="glass-card h-[400px] overflow-hidden relative glow-teal flex items-center justify-center reveal-on-scroll">
                    <img alt="Deploy Agents" className="w-full h-full object-cover opacity-50" src="https://lh3.googleusercontent.com/aida-public/AB6AXuAU56d66hhYLAwprSHxRpEZzdXmTutZuZXDehPBnmDeKH1zpYQ4fdVIChmqrWMoF67RQo5DBM0TvOsFqGrsy6tXlGDbzAO09lKx5fvXZy-TjWfb83Vvr9ptY1ZAk_lFPlNY40QBpQwE2i0HnBkL0P_xIrBSiV3ceN4IY7ap8nf1f4txrQSqr5PrPlObFHh_Nu48hmChXwQcphYupFmjHxrlElDJhfDjanAJXs8DfdJ8TfuEPLfH4keN_T_vchLmi7abSmsA9ZsCtG0" />
                    <div className="absolute inset-0 bg-gradient-to-t from-background to-transparent"></div>
                    <div className="absolute bottom-lg left-lg right-lg p-md glass-card flex items-center gap-md">
                      <span className="material-symbols-outlined text-[#14B8A6] animate-pulse">rocket_launch</span>
                      <span className="font-label-caps text-label-caps text-xs">AGENTS ACTIVE: 04</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Step 3 */}
              <div className="flex flex-col md:flex-row items-center gap-xl w-full">
                <div className="flex-1 reveal-on-scroll">
                  <span className="font-display-countdown text-[120px] text-warning-amber/40 leading-none font-mono">03</span>
                  <h3 className="font-headline-lg text-headline-lg mb-md">ZeroHour Flow</h3>
                  <p className="text-on-surface-variant font-body-lg text-body-lg" style={{ maxWidth: '448px' }}>
                    Enter the 'Flow State'. The dashboard reveals only the most critical action required right now, silencing everything else.
                  </p>
                </div>
                <div className="flex-1 w-full">
                  <div className="glass-card h-[400px] overflow-hidden relative glow-amber flex items-center justify-center reveal-on-scroll">
                    <img alt="Flow Mode" className="w-full h-full object-cover opacity-50" src="https://lh3.googleusercontent.com/aida-public/AB6AXuCNWtvlhlqFYDUXIMNKaYZkmyfya9tSpBdTLKcQ16PkI5InGFz3ir5rzzF6ZSkRDndGoMQJrWLogXOXFVfbVYXL_u95YqEMgyFYxmDljKS8Pr3YC5tzQ4fMnbze2AuBkQLw8AI0R2B_eXFXUUAuJaBlsG8Say7aEI7thjJFL5zsW2t41lvQfwq3LmG-pYhCg5yuUluv5z9IJlfzdnRqelg2fxuxUx2LuF2WVl2eaP-ssKFS4AurVLDY11uE5BaDduJat7AutXlrZjg" />
                    <div className="absolute inset-0 bg-gradient-to-t from-background to-transparent"></div>
                    <div className="absolute bottom-lg left-lg right-lg p-md glass-card flex items-center gap-md">
                      <span className="material-symbols-outlined text-[#F59E0B] animate-pulse">center_focus_strong</span>
                      <span className="font-label-caps text-label-caps text-xs">CURRENT FOCUS: MISSION CRITICAL</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Evaluation Scoreboard */}
        <section className="py-24 px-margin-desktop mx-auto overflow-hidden w-full max-w-7xl">
          <h2 className="font-headline-lg text-headline-lg text-center mb-16 reveal-on-scroll">Agent Capability Matrix</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-md w-full">
            {/* Score Cards */}
            <div className="scoreboard-card glass-card p-md flex flex-col h-[300px] justify-between group reveal-on-scroll">
              <div className="text-[12px] font-label-caps text-on-surface-variant opacity-60 writing-mode-vertical rotate-180 mb-md self-center">Problem Solving</div>
              <div className="flex flex-col items-center">
                <div className="w-1 bg-[#27272A] h-32 rounded-full relative overflow-hidden">
                  <div className="absolute bottom-0 left-0 w-full bg-primary rounded-full group-hover:h-[95%] h-[85%] transition-all duration-1000 ease-out"></div>
                </div>
                <span className="font-display-countdown text-[18px] mt-md font-mono">95</span>
              </div>
            </div>

            <div className="scoreboard-card glass-card p-md flex flex-col h-[300px] justify-between group reveal-on-scroll">
              <div className="text-[12px] font-label-caps text-on-surface-variant opacity-60 writing-mode-vertical rotate-180 mb-md self-center">Agentic Depth</div>
              <div className="flex flex-col items-center">
                <div className="w-1 bg-[#27272A] h-32 rounded-full relative overflow-hidden">
                  <div className="absolute bottom-0 left-0 w-full bg-primary rounded-full group-hover:h-[92%] h-[72%] transition-all duration-1000 ease-out"></div>
                </div>
                <span className="font-display-countdown text-[18px] mt-md font-mono">92</span>
              </div>
            </div>

            <div className="scoreboard-card glass-card p-md flex flex-col h-[300px] justify-between group reveal-on-scroll">
              <div className="text-[12px] font-label-caps text-on-surface-variant opacity-60 writing-mode-vertical rotate-180 mb-md self-center">Temporal Accuracy</div>
              <div className="flex flex-col items-center">
                <div className="w-1 bg-[#27272A] h-32 rounded-full relative overflow-hidden">
                  <div className="absolute bottom-0 left-0 w-full bg-primary rounded-full group-hover:h-[98%] h-[88%] transition-all duration-1000 ease-out"></div>
                </div>
                <span className="font-display-countdown text-[18px] mt-md font-mono">98</span>
              </div>
            </div>

            <div className="scoreboard-card glass-card p-md flex flex-col h-[300px] justify-between group reveal-on-scroll">
              <div className="text-[12px] font-label-caps text-on-surface-variant opacity-60 writing-mode-vertical rotate-180 mb-md self-center">Context Retention</div>
              <div className="flex flex-col items-center">
                <div className="w-1 bg-[#27272A] h-32 rounded-full relative overflow-hidden">
                  <div className="absolute bottom-0 left-0 w-full bg-primary rounded-full group-hover:h-[84%] h-[64%] transition-all duration-1000 ease-out"></div>
                </div>
                <span className="font-display-countdown text-[18px] mt-md font-mono">84</span>
              </div>
            </div>

            <div className="scoreboard-card glass-card p-md flex flex-col h-[300px] justify-between group reveal-on-scroll">
              <div className="text-[12px] font-label-caps text-on-surface-variant opacity-60 writing-mode-vertical rotate-180 mb-md self-center">Logical Consistency</div>
              <div className="flex flex-col items-center">
                <div className="w-1 bg-[#27272A] h-32 rounded-full relative overflow-hidden">
                  <div className="absolute bottom-0 left-0 w-full bg-primary rounded-full group-hover:h-[90%] h-[80%] transition-all duration-1000 ease-out"></div>
                </div>
                <span className="font-display-countdown text-[18px] mt-md font-mono">90</span>
              </div>
            </div>

            <div className="scoreboard-card glass-card p-md flex flex-col h-[300px] justify-between group reveal-on-scroll">
              <div className="text-[12px] font-label-caps text-on-surface-variant opacity-60 writing-mode-vertical rotate-180 mb-md self-center">Tool Mastery</div>
              <div className="flex flex-col items-center">
                <div className="w-1 bg-[#27272A] h-32 rounded-full relative overflow-hidden">
                  <div className="absolute bottom-0 left-0 w-full bg-primary rounded-full group-hover:h-[87%] h-[77%] transition-all duration-1000 ease-out"></div>
                </div>
                <span className="font-display-countdown text-[18px] mt-md font-mono">87</span>
              </div>
            </div>

            <div className="scoreboard-card glass-card p-md flex flex-col h-[300px] justify-between group reveal-on-scroll">
              <div className="text-[12px] font-label-caps text-on-surface-variant opacity-60 writing-mode-vertical rotate-180 mb-md self-center">Self-Correction</div>
              <div className="flex flex-col items-center">
                <div className="w-1 bg-[#27272A] h-32 rounded-full relative overflow-hidden">
                  <div className="absolute bottom-0 left-0 w-full bg-primary rounded-full group-hover:h-[94%] h-[74%] transition-all duration-1000 ease-out"></div>
                </div>
                <span className="font-display-countdown text-[18px] mt-md font-mono">94</span>
              </div>
            </div>
          </div>
        </section>

        {/* Quote / Value Section */}
        <section className="py-32 relative overflow-hidden w-full text-center bg-transparent">
          {/* Pulsing Portal Background */}
          <div className="absolute inset-0 z-0 flex items-center justify-center pointer-events-none">
            <div className="w-[600px] h-[600px] rounded-full pulse-glow-red" style={{ background: 'radial-gradient(circle, rgba(255, 69, 58, 0.18) 0%, transparent 70%)' }} />
          </div>
          
          <div className="relative z-10 px-margin-mobile max-w-4xl mx-auto">
            <div className="mb-md text-[#FF453A] animate-pulse">
              <span className="material-symbols-outlined text-4xl">format_quote</span>
            </div>
            <h2 className="font-headline-lg text-[28px] md:text-[36px] font-bold tracking-tight text-white mb-6 leading-relaxed reveal-on-scroll">
              "Never drop the ball again. ZeroHour maps your obligations into a structured survival protocol, transforming chaos into an automated, step-by-step path to victory."
            </h2>
            <div className="w-16 h-[2px] bg-[#FF453A] mx-auto mb-6"></div>
            <p className="text-[#FF453A] font-mono text-xs uppercase tracking-[0.2em] font-bold">
              Mission Directive // Absolute Deadline Execution
            </p>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="w-full py-xl border-t border-subtle bg-surface-container-lowest relative z-10">
        <div className="flex flex-col md:flex-row justify-between items-center px-margin-desktop gap-md mx-auto w-full max-w-7xl">
          <div className="flex flex-col items-center md:items-start gap-xs">
            <div className="font-headline-md text-headline-md font-black text-on-surface">ZeroHour</div>
            <div className="font-body-md text-body-md text-muted-gray">© 2026 ZeroHour AI. Command your time.</div>
          </div>
          <div className="flex gap-xl flex-wrap justify-center">
            <Link className="text-muted-gray hover:text-on-surface transition-colors font-label-caps text-label-caps" to="/about">About Us</Link>
            <Link className="text-muted-gray hover:text-on-surface transition-colors font-label-caps text-label-caps" to="/privacy">Privacy</Link>
            <Link className="text-muted-gray hover:text-on-surface transition-colors font-label-caps text-label-caps" to="/terms">Terms</Link>
            <Link className="text-muted-gray hover:text-on-surface transition-colors font-label-caps text-label-caps" to="/security">Security</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
