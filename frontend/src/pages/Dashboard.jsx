import { useState, useEffect, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Loader2, RefreshCw } from 'lucide-react';
import { api } from '../services/api';
import TaskCard from '../components/TaskCard';
import NotificationBell from '../components/NotificationBell';
import AgentLogPanel from '../components/AgentLogPanel';
import OnboardingModal from '../components/OnboardingModal';
import { subscribeToAgentStream } from '../services/sse';

const PAGE_LOAD_TIME = Date.now();

export default function Dashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showOnboarding, setShowOnboarding] = useState(false);
  const [error, setError] = useState(null);

  // Set document title
  useEffect(() => {
    document.title = 'ZeroHour — Dashboard';
  }, []);

  // New task form fields
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [deadline, setDeadline] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // SSE tracking states
  const [activeSessionId, setActiveSessionId] = useState(null);
  const [sseLogs, setSseLogs] = useState([]);
  const [sseCompleted, setSseCompleted] = useState(false);

  // Filters & Sorting states
  const [activeFilter, setActiveFilter] = useState('ALL'); // 'ALL' | 'TODAY' | 'OVERDUE' | 'DONE'
  const [sortBy, setSortBy] = useState('DEADLINE'); // 'DEADLINE' | 'PRIORITY' | 'CREATED'
  const [refreshKey, setRefreshKey] = useState(0);

  const fetchUserDataAndTasks = useCallback(async () => {
    try {
      const userData = await api.getMe();
      setUser(userData);
      
      const isLocalOnboarded = localStorage.getItem('zerohour_onboarded') === 'true';
      if ((userData.onboarded === false || !userData.onboarded) && !isLocalOnboarded) {
        setShowOnboarding(true);
      }

      const taskList = await api.getTasks();
      setTasks(taskList);
      setRefreshKey((prev) => prev + 1);
    } catch (e) {
      console.error(e);
      if (e.message === 'Unauthorized') {
        navigate('/');
      } else {
        setError('Failed to sync operations. Please check connection and refresh.');
      }
    } finally {
      setLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    let active = true;
    const load = async () => {
      if (active) {
        await fetchUserDataAndTasks();
      }
    };
    load();
    return () => {
      active = false;
    };
  }, [fetchUserDataAndTasks]);

  // Subscribe to agent SSE log streams if task generation triggers
  useEffect(() => {
    if (!activeSessionId) return;

    const connection = subscribeToAgentStream(
      activeSessionId,
      (data) => {
        setSseLogs((prev) => [...prev, data]);
        if (data.status === 'DONE' && data.agent === 'PrioritizerAgent') {
          setSseCompleted(true);
          setTimeout(() => {
            fetchUserDataAndTasks();
            setActiveSessionId(null);
          }, 2000);
        }
      },
      (err) => {
        console.error(err);
      }
    );

    return () => connection.close();
  }, [activeSessionId, fetchUserDataAndTasks]);

  const handleLogout = () => {
    localStorage.removeItem('zerohour_onboarded');
    window.location.href = api.logoutUrl;
  };

  const handleCreateTask = async (e) => {
    e.preventDefault();
    if (!title || !deadline) return;

    setIsSubmitting(true);
    try {
      const payload = {
        title,
        description,
        deadline: new Date(deadline).toISOString(),
      };
      
      const res = await api.createTask(payload);
      
      // Initialize SSE logs state before connecting to the stream
      setSseLogs([]);
      setSseCompleted(false);
      setActiveSessionId(res.sessionId);

      // Reset form
      setTitle('');
      setDescription('');
      setDeadline('');
    } catch (e) {
      console.error(e);
    } finally {
      setIsSubmitting(false);
    }
  };

  const filteredTasks = tasks.filter(t => {
    if (activeFilter === 'DONE') {
      return t.status === 'DONE';
    }
    
    if (t.status === 'DONE') return false;

    if (activeFilter === 'OVERDUE') {
      const isPast = new Date(t.deadline).getTime() < PAGE_LOAD_TIME;
      return isPast && t.status !== 'DONE';
    }

    if (activeFilter === 'TODAY') {
      const deadlineDate = new Date(t.deadline);
      const today = new Date();
      return deadlineDate.toDateString() === today.toDateString();
    }

    return true;
  });

  const priorityWeight = { 'CRITICAL': 4, 'HIGH': 3, 'MEDIUM': 2, 'LOW': 1 };
  
  const sortedTasks = [...filteredTasks].sort((a, b) => {
    if (a.priority === 'CRITICAL' && b.priority !== 'CRITICAL') return -1;
    if (b.priority === 'CRITICAL' && a.priority !== 'CRITICAL') return 1;

    if (sortBy === 'PRIORITY') {
      const wA = priorityWeight[a.priority] || 2;
      const wB = priorityWeight[b.priority] || 2;
      return wB - wA;
    }

    if (sortBy === 'CREATED') {
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    }

    return new Date(a.deadline).getTime() - new Date(b.deadline).getTime();
  });

  const criticalCount = tasks.filter(t => t.priority === 'CRITICAL' && t.status !== 'DONE').length;

  if (loading) {
    return (
      <div className="min-h-screen bg-[#0D0D0D] flex flex-col justify-center items-center gap-4">
        <Loader2 className="w-10 h-10 text-[#FF453A] animate-spin" />
        <p className="font-mono text-xs text-[#FF453A] uppercase tracking-[0.2em] animate-pulse">
          Accessing Command Room...
        </p>
      </div>
    );
  }

  return (
    <div className="dashboard-layout bg-[#0D0D0D] animate-fadeIn">
      
      {/* Onboarding Tutorial Modal */}
      {showOnboarding && (
        <OnboardingModal onClose={() => setShowOnboarding(false)} />
      )}

      {/* Side Navigation Bar */}
      <nav className="w-64 flex-shrink-0 h-screen sticky top-0 flex flex-col py-6 glass-panel z-40 border-r border-white/10 rounded-r-2xl">
        <div className="px-6 mb-8">
          <h1 className="font-display text-3xl font-black text-white tracking-tighter italic">ZEROHOUR</h1>
          <p className="font-mono text-[9px] text-[#FF453A] uppercase tracking-[0.3em] mt-1 opacity-80">COMMANDER_TERMINAL</p>
        </div>

        <div className="flex flex-col gap-1 flex-grow">
          <a className="flex items-center gap-4 text-white bg-white/5 border-l-4 border-[#FF453A] px-6 py-3 transition-all font-mono text-xs tracking-wider font-bold" href="#">
            <span className="material-symbols-outlined text-[#FF453A]" style={{ fontVariationSettings: "'FILL' 1" }}>dashboard</span>
            DASHBOARD
          </a>
          <a className="flex items-center gap-4 text-[#94a3b8] px-6 py-3 hover:bg-white/5 hover:text-white transition-all font-mono text-xs tracking-wider" href="https://calendar.google.com" target="_blank" rel="noreferrer">
            <span className="material-symbols-outlined">calendar_today</span>
            CALENDAR
          </a>
          <Link to="/settings" className="flex items-center gap-4 text-[#94a3b8] px-6 py-3 hover:bg-white/5 hover:text-white transition-all font-mono text-xs tracking-wider">
            <span className="material-symbols-outlined">settings</span>
            SETTINGS
          </Link>
        </div>

        {/* Panic Button at Navigation Bottom */}
        <div className="px-6 mt-auto pb-8">
          <Link 
            to="/panic" 
            className="panic-pulse w-full flex items-center justify-center gap-2 py-4 rounded-xl bg-[#FF453A] text-white font-black transition-all hover:brightness-115 active:scale-95 shadow-lg shadow-[#FF453A]/20"
          >
            <span className="material-symbols-outlined text-sm">warning</span>
            <span className="uppercase text-[10px] tracking-widest font-mono">Panic Mode</span>
          </Link>
        </div>
      </nav>

      {/* Main Content Wrapper */}
      <main className="flex-grow min-h-screen flex flex-col relative bg-[#0D0D0D]">
        
        {/* Header */}
        <header className="h-16 flex justify-between items-center px-12 border-b border-white/10 glass-panel z-30 sticky top-0">
          <div className="flex items-center gap-3">
            <span className="font-mono text-xs text-[#94a3b8] uppercase tracking-widest">
              SECURE SESSION: <span className="text-[#FF453A]">{user?.displayName?.toUpperCase() || 'DEVELOPER'}</span>
            </span>
          </div>

          <div className="flex items-center gap-8">
            <NotificationBell />

            <div className="flex items-center gap-3">
              <div className="flex flex-col items-end text-[10px] font-mono leading-none">
                <span className="text-white font-bold">{user?.displayName || 'Developer'}</span>
                <span className="text-gray-500 mt-1">{user?.email}</span>
              </div>
              <div className="w-8 h-8 rounded-full border border-white/20 overflow-hidden grayscale hover:grayscale-0 transition-all cursor-pointer">
                <img alt="Profile" className="w-full h-full object-cover" src="https://lh3.googleusercontent.com/aida-public/AB6AXuArY0quddWGUfmAcrVTffEwp6FXxXiE9N5o_suIIbfZuiR7NOvLAiCwJslBwcvM88Ygvhhj0j3MS4lVSKyaXaUzzQhiP4c43qdp9UXgFSIKfP2DKTpZ0CGxeAyeMVYAfeCnEm7VVSRycl5vhkXrsJ4xc1CR3ms6EywhyTWoiqk_LIRygxhPK9y4-hFXgpB0pfB6VeodPyee5DqaVAXSZoC0M0AsAM563vTkC2rkKTbOcbXVzWIwzspMbTxKNPo4uqnQpnTtPw7HCtw" />
              </div>
            </div>

            <button 
              onClick={handleLogout}
              className="p-2 rounded-lg hover:bg-white/5 text-gray-400 hover:text-white transition-all"
              title="Log out"
            >
              <span className="material-symbols-outlined">logout</span>
            </button>
          </div>
        </header>

        {/* Dashboard Grid Content */}
        <section className="flex-grow p-10 overflow-y-auto bg-[#0D0D0D] relative">
          
          {error && (
            <div className="bg-red-950 border border-red-800 text-red-300 px-4 py-3 rounded-lg mb-6 text-xs flex justify-between items-center font-mono">
              <span>{error}</span>
              <button onClick={() => setError(null)} className="text-red-400 font-bold hover:text-white transition-colors">✕</button>
            </div>
          )}

          {/* Agent log streaming panel */}
          {activeSessionId && (
            <AgentLogPanel logs={sseLogs} isCompleted={sseCompleted} />
          )}

          {/* Operation Central Title Area */}
          <div className="flex flex-col md:flex-row justify-between items-start md:items-end mb-12 gap-6">
            <div>
              <h2 className="font-display text-5xl md:text-6xl font-black text-white tracking-tighter uppercase italic leading-none">
                Operation<br />
                <span className="text-outline">Central</span>
              </h2>
              <div className="flex items-center gap-4 mt-6">
                <div className="w-16 h-[2px] bg-[#FF453A]"></div>
                <p className="text-[#FF453A] uppercase text-[9px] font-mono tracking-widest font-bold">
                  Commander, {criticalCount} critical items pending.
                </p>
              </div>
            </div>

            {/* Filter Tabs */}
            <div className="flex bg-white/5 p-1 rounded-lg border border-white/10 glass-panel font-mono text-[9px] tracking-widest font-bold">
              <button 
                onClick={() => setActiveFilter('ALL')}
                className={`px-5 py-2 rounded transition-all uppercase ${
                  activeFilter === 'ALL' ? 'bg-[#FF453A] text-white' : 'text-gray-400 hover:text-white'
                }`}
              >
                ALL
              </button>
              <button 
                onClick={() => setActiveFilter('TODAY')}
                className={`px-5 py-2 rounded transition-all uppercase ${
                  activeFilter === 'TODAY' ? 'bg-[#FF453A] text-white' : 'text-gray-400 hover:text-white'
                }`}
              >
                TODAY
              </button>
              <button 
                onClick={() => setActiveFilter('OVERDUE')}
                className={`px-5 py-2 rounded transition-all uppercase ${
                  activeFilter === 'OVERDUE' ? 'bg-[#FF453A] text-white' : 'text-gray-400 hover:text-white'
                }`}
              >
                OVERDUE
              </button>
              <button 
                onClick={() => setActiveFilter('DONE')}
                className={`px-5 py-2 rounded transition-all uppercase ${
                  activeFilter === 'DONE' ? 'bg-[#FF453A] text-white' : 'text-gray-400 hover:text-white'
                }`}
              >
                DONE
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            
            {/* Left Side: Tasks Grid list */}
            <div className="lg:col-span-2 flex flex-col gap-6">
              
              <div className="flex justify-between items-center">
                <h2 className="text-sm font-mono tracking-widest uppercase flex items-center gap-2 text-white">
                  <span className="material-symbols-outlined text-base">assignment</span>
                  Active survival paths
                </h2>
                <div className="flex items-center gap-3">
                  {/* Sorting dropdown */}
                  <select 
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value)}
                    className="font-mono text-[9px] uppercase bg-white/5 border border-white/10 rounded px-2 py-1 text-gray-300 focus:border-[#FF453A] tracking-wider cursor-pointer"
                  >
                    <option value="DEADLINE">Sort: Deadline</option>
                    <option value="PRIORITY">Sort: Priority</option>
                    <option value="CREATED">Sort: Created</option>
                  </select>

                  <button 
                    onClick={fetchUserDataAndTasks}
                    className="p-1 rounded hover:bg-white/5 text-[#94a3b8] hover:text-white transition-colors"
                    title="Refresh data"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              {sortedTasks.length === 0 ? (
                <div className="glass-panel p-16 text-center flex flex-col items-center gap-6 border-dashed border-white/10">
                  <div className="w-12 h-12 bg-white/5 rounded-lg flex items-center justify-center border border-white/10 ring-1 ring-white/5">
                    <span className="material-symbols-outlined text-[#94a3b8] text-2xl">terminal</span>
                  </div>
                  <div>
                    <h3 className="text-base font-black uppercase tracking-tight text-white italic">System Standby</h3>
                    <p className="text-[10px] text-gray-500 max-w-sm mt-3 uppercase tracking-wider leading-relaxed">
                      Sector analysis complete. All secondary nodes optimized. Initiate next sequence or trigger Panic Mode.
                    </p>
                  </div>
                  <Link to="/panic" className="btn btn-primary py-2 px-6 text-[10px]">
                    Activate Panic Session
                  </Link>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 animate-slide-up">
                  {sortedTasks.map((task) => (
                    <TaskCard 
                      key={`${task.id}-${refreshKey}`} 
                      task={task} 
                      subtaskCount={task.subtaskCount}
                      completedSubtasks={task.completedSubtasks}
                    />
                  ))}
                </div>
              )}

            </div>

            {/* Right Side: Fast Task Form */}
            <div>
              <div className="glass-panel p-6 sticky top-24 border-white/10">
                <h3 className="text-md font-black uppercase tracking-tight text-white mb-1.5 flex items-center gap-2">
                  <span className="material-symbols-outlined text-[#FF453A] text-base">add</span>
                  Add urgent goal
                </h3>
                <p className="text-[10px] text-gray-400 uppercase tracking-wider mb-6">
                  Input deadline and context. We'll deploy AI agents.
                </p>

                <form onSubmit={handleCreateTask} className="flex flex-col gap-4">
                  <div className="flex flex-col gap-1.5">
                    <label className="text-[9px] font-mono font-bold tracking-widest uppercase text-gray-400">Goal Title</label>
                    <input 
                      type="text" 
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                      placeholder="e.g. Study for physics midterm" 
                      className="input-field"
                      required
                    />
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <label className="text-[9px] font-mono font-bold tracking-widest uppercase text-gray-400">Context / Details (Optional)</label>
                    <textarea 
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      placeholder="Chapters covered, relevant links, worries..." 
                      className="input-field h-24 resize-none"
                    />
                  </div>

                  <div className="flex flex-col gap-1.5">
                    <label className="text-[9px] font-mono font-bold tracking-widest uppercase text-gray-400">Hard Deadline (Asia/Kolkata)</label>
                    <input 
                      type="datetime-local" 
                      value={deadline}
                      onChange={(e) => setDeadline(e.target.value)}
                      className="input-field font-mono"
                      required
                    />
                  </div>

                  <button 
                    type="submit" 
                    disabled={isSubmitting}
                    className="btn btn-primary w-full mt-2 py-4 flex items-center justify-center gap-2 text-[10px]"
                  >
                    {isSubmitting ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        Generating plan...
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined text-sm">add</span>
                        Create Task Schedule
                      </>
                    )}
                  </button>
                </form>
              </div>
            </div>

          </div>
        </section>

        {/* Stats Footer */}
        <footer className="h-10 border-t border-white/10 glass-panel px-8 flex items-center justify-between z-30 font-mono text-[9px] text-[#94a3b8] tracking-widest uppercase mt-auto">
          <div className="flex items-center gap-10">
            <div className="flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-[#14B8A6] shadow-[0_0_5px_rgba(20,184,166,0.6)]"></span>
              <span>Systems Nominal</span>
            </div>
            <div>UPTIME: <span className="text-white">99.98%</span></div>
            <div>LATENCY: <span className="text-white">12ms</span></div>
          </div>
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-[12px] text-[#FF453A]">verified_user</span>
            <span>ENCRYPTED_SESSION: SEC_882_CMD</span>
          </div>
        </footer>

      </main>

      {/* Mobile Bottom Navigation Bar */}
      <nav className="fixed bottom-0 left-0 right-0 h-16 bg-[#0D0D0D]/95 backdrop-blur-md border-t border-white/10 z-40 flex justify-around items-center md:hidden px-4 shadow-[0_-10px_20px_rgba(0,0,0,0.5)]">
        <Link to="/dashboard" className="flex flex-col items-center gap-1 text-[#14B8A6] transition-all">
          <span className="material-symbols-outlined text-lg">dashboard</span>
          <span className="text-[8px] font-mono tracking-widest uppercase">Core</span>
        </Link>
        <a href="https://calendar.google.com" target="_blank" rel="noreferrer" className="flex flex-col items-center gap-1 text-[#94a3b8] hover:text-white transition-all">
          <span className="material-symbols-outlined text-lg">calendar_today</span>
          <span className="text-[8px] font-mono tracking-widest uppercase">Cal</span>
        </a>
        <Link to="/panic" className="flex flex-col items-center gap-1 text-[#FF453A] animate-pulse">
          <span className="material-symbols-outlined text-lg">emergency</span>
          <span className="text-[8px] font-mono tracking-widest uppercase font-bold">Panic</span>
        </Link>
        <Link to="/settings" className="flex flex-col items-center gap-1 text-[#94a3b8] hover:text-white transition-all">
          <span className="material-symbols-outlined text-lg">settings</span>
          <span className="text-[8px] font-mono tracking-widest uppercase">Set</span>
        </Link>
        <button onClick={handleLogout} className="flex flex-col items-center gap-1 text-[#94a3b8] hover:text-white transition-all bg-transparent border-0 p-0 cursor-pointer">
          <span className="material-symbols-outlined text-lg">logout</span>
          <span className="text-[8px] font-mono tracking-widest uppercase">Exit</span>
        </button>
      </nav>
    </div>
  );
}
