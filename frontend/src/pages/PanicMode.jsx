import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Send, Loader2, Paperclip } from 'lucide-react';
import { api } from '../services/api';
import { subscribeToAgentStream } from '../services/sse';
import AgentLogPanel from '../components/AgentLogPanel';
import PlanPreview from '../components/PlanPreview';

const THINKING_QUOTES = [
  "Intercepting upcoming deadline panic...",
  "Deploying PlannerAgent: Deconstructing goal into atomic subtasks...",
  "PlannerAgent: Customizing time blocks for maximum efficiency...",
  "Deploying PrioritizerAgent: Weighing urgency vs cognitive impact...",
  "PrioritizerAgent: Evaluating task dependencies and timeline critical path...",
  "SchedulerAgent: Calculating calendar gaps and booking sync slots...",
  "NudgeAgent: Formatting Gmail alerts and local notification thresholds...",
  "ZeroHour System: Synthesizing final tactical schedule...",
  "Optimizing results: Slicing workload, securing breaks...",
];

export default function PanicMode() {
  const messagesEndRef = useRef(null);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const taskIdFromUrl = searchParams.get('taskId');
  const [targetTask, setTargetTask] = useState(null);

  useEffect(() => {
    if (taskIdFromUrl) {
      api.getTask(taskIdFromUrl)
        .then(t => setTargetTask(t))
        .catch(err => console.error('Failed to load task context:', err));
    }
  }, [taskIdFromUrl]);

  // Flow State: 'chat' | 'thinking' | 'preview' | 'confirming_agents' | 'confirmed'
  const [flowState, setFlowState] = useState('chat');
  const [sessionId, setSessionId] = useState(null);
  const [sseSessionId, setSseSessionId] = useState(null);
  
  // Chatting States
  const [messages, setMessages] = useState([
    { role: 'assistant', content: "ZeroHour activated. What's causing the panic? Tell me your deadline situation." }
  ]);
  const [userInput, setUserInput] = useState('');
  const [isSending, setIsSending] = useState(false);

  // Agent SSE Thinking States
  const [sseLogs, setSseLogs] = useState([]);
  const [sseCompleted, setSseCompleted] = useState(false);

  // Generated Plan States
  const [generatedSubtasks, setGeneratedSubtasks] = useState([]);
  const [finalDeadline, setFinalDeadline] = useState(null);

  // Rotating Thinking Quotations State
  const [activeQuoteIndex, setActiveQuoteIndex] = useState(0);

  // Incremental Plan Stream States
  const [receivedSubtasks, setReceivedSubtasks] = useState([]);
  const [displayedSubtasks, setDisplayedSubtasks] = useState([]);

  // Sessions History States
  const [sessions, setSessions] = useState([]);
  const [editingSessionId, setEditingSessionId] = useState(null);
  const [editTitle, setEditTitle] = useState('');

  // Attachment States
  const [attachment, setAttachment] = useState(null);
  const fileInputRef = useRef(null);

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;

    if (file.size > 4 * 1024 * 1024) {
      alert("File size exceeds 4MB limit.");
      return;
    }

    const reader = new FileReader();
    reader.onloadend = () => {
      setAttachment({
        fileName: file.name,
        mimeType: file.type,
        base64Data: reader.result.split(',')[1]
      });
    };
    reader.readAsDataURL(file);
  };

  const removeAttachment = () => {
    setAttachment(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  // Load Sessions List
  const fetchSessions = async () => {
    try {
      const list = await api.getPanicSessions();
      list.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      setSessions(list);
    } catch (e) {
      console.error("Failed to load panic sessions history list", e);
    }
  };

  useEffect(() => {
    document.title = 'ZeroHour — Panic Mode 🚨';
    fetchSessions();
  }, []);

  // Shared Session Loading Logic
  const loadSession = async (id, flowStateOverride = null) => {
    try {
      const session = await api.getPanicSession(id);
      const chatHistory = await api.getPanicConversation(id);
      
      if (chatHistory && chatHistory.length > 0) {
        setMessages(chatHistory);
      } else {
        setMessages([
          { role: 'assistant', content: "ZeroHour activated. What's causing the panic? Tell me your deadline situation." }
        ]);
      }

      setSessionId(id);
      const computedSseId = session.sseSessionId || `panic-${id}`;
      setSseSessionId(computedSseId);
      localStorage.setItem('zerohour_panic_session_id', id);
      localStorage.setItem('zerohour_panic_sse_session_id', computedSseId);

      if (session.status === 'CONFIRMED') {
        setFlowState('confirmed');
        const totalMinutes = session.subtasks ? session.subtasks.reduce((sum, s) => sum + s.durationMinutes, 0) : 120;
        setFinalDeadline(new Date(new Date(session.createdAt || Date.now()).getTime() + totalMinutes * 60 * 1000));
        return;
      }

      if (session.subtasks && session.subtasks.length > 0) {
        setGeneratedSubtasks(session.subtasks);
        setReceivedSubtasks(session.subtasks);
        setDisplayedSubtasks(session.subtasks);
      } else {
        setGeneratedSubtasks([]);
        setReceivedSubtasks([]);
        setDisplayedSubtasks([]);
      }

      if (flowStateOverride) {
        setFlowState(flowStateOverride);
      } else if (session.status === 'PLAN_READY') {
        setFlowState('preview');
      } else if (session.status === 'GENERATING') {
        setFlowState('thinking');
      } else {
        setFlowState('chat');
      }
    } catch (err) {
      console.error("Failed to load panic session", err);
    }
  };

  // Restore Panic Session on Mount
  useEffect(() => {
    const savedSessionId = localStorage.getItem('zerohour_panic_session_id');
    const savedFlowState = localStorage.getItem('zerohour_panic_flow_state');

    if (savedSessionId) {
      loadSession(savedSessionId, savedFlowState);
    }
  }, []);

  // Sync flowState to localStorage
  useEffect(() => {
    if (sessionId) {
      localStorage.setItem('zerohour_panic_flow_state', flowState);
    }
  }, [flowState, sessionId]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isSending]);

  // Subscribe to agent SSE log stream if in 'thinking' mode
  useEffect(() => {
    if (flowState !== 'thinking' || !sseSessionId) return;

    // Reset incremental states
    setReceivedSubtasks([]);
    setDisplayedSubtasks([]);

    const connection = subscribeToAgentStream(
      sseSessionId,
      (data) => {
        setSseLogs((prev) => [...prev, data]);

        // Check for PlannerAgent subtask payload
        if (data.agent === 'PlannerAgent' && data.status === 'DONE' && data.payload) {
          try {
            const subtasks = JSON.parse(data.payload);
            setReceivedSubtasks(subtasks);
          } catch (e) {
            console.error('Failed to parse subtasks from PlannerAgent payload', e);
          }
        }

        if (data.status === 'DONE' && data.agent === 'PrioritizerAgent') {
          setSseCompleted(true);
          setTimeout(async () => {
            try {
              const session = await api.getPanicSession(sessionId);
              if (session.subtasks && session.subtasks.length > 0) {
                setGeneratedSubtasks(session.subtasks);
                setFlowState('preview');
              }
            } catch (e) {
              console.error('Failed to load plan json', e);
            }
          }, 2000);
        }
      },
      (err) => {
        console.error(err);
      }
    );

    return () => connection.close();
  }, [flowState, sseSessionId, sessionId]);

  // Effect to rotate thinking quotes
  useEffect(() => {
    if (flowState !== 'thinking') return;
    const interval = setInterval(() => {
      setActiveQuoteIndex((prev) => (prev + 1) % THINKING_QUOTES.length);
    }, 2000);
    return () => clearInterval(interval);
  }, [flowState]);

  // Effect to incrementally reveal subtasks
  useEffect(() => {
    if (receivedSubtasks.length === 0) return;
    let idx = 0;
    const timer = setInterval(() => {
      if (idx < receivedSubtasks.length) {
        setDisplayedSubtasks((prev) => [...prev, receivedSubtasks[idx]]);
        idx++;
      } else {
        clearInterval(timer);
      }
    }, 450);
    return () => clearInterval(timer);
  }, [receivedSubtasks]);

  const handleAbort = () => {
    localStorage.removeItem('zerohour_panic_session_id');
    localStorage.removeItem('zerohour_panic_flow_state');
    localStorage.removeItem('zerohour_panic_sse_session_id');
    navigate('/dashboard');
  };

  const handleStartNewSession = () => {
    localStorage.removeItem('zerohour_panic_session_id');
    localStorage.removeItem('zerohour_panic_flow_state');
    localStorage.removeItem('zerohour_panic_sse_session_id');
    setSessionId(null);
    setSseSessionId(null);
    setMessages([
      { role: 'assistant', content: "ZeroHour activated. What's causing the panic? Tell me your deadline situation." }
    ]);
    setSseLogs([]);
    setSseCompleted(false);
    setGeneratedSubtasks([]);
    setReceivedSubtasks([]);
    setDisplayedSubtasks([]);
    setFlowState('chat');
  };

  const handleRenameSession = async (id, title) => {
    if (!title.trim()) return;
    try {
      await api.renamePanicSession(id, title);
      setEditingSessionId(null);
      fetchSessions();
    } catch (e) {
      console.error(e);
    }
  };

  const handleDeleteSession = async (id) => {
    if (!window.confirm("Are you sure you want to delete this panic session? This cannot be undone.")) return;
    try {
      await api.deletePanicSession(id);
      if (sessionId === id) {
        handleStartNewSession();
      }
      fetchSessions();
    } catch (e) {
      console.error(e);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('zerohour_onboarded');
    window.location.href = api.logoutUrl;
  };

  const handleSendMessage = async (e) => {
    e.preventDefault();
    if ((!userInput.trim() && !attachment) || isSending) return;

    const messageText = userInput;
    const currentAttachment = attachment;

    let displayMessage = messageText;
    if (currentAttachment) {
      displayMessage += `\n[Attachment: ${currentAttachment.fileName}]`;
    }

    setMessages((prev) => [...prev, { role: 'user', content: displayMessage }]);
    setUserInput('');
    setAttachment(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
    setIsSending(true);

    try {
      if (!sessionId) {
        const res = await api.startPanic(messageText, currentAttachment, taskIdFromUrl);
        setSessionId(res.sessionId);
        setSseSessionId(res.sseSessionId);
        localStorage.setItem('zerohour_panic_session_id', res.sessionId);
        localStorage.setItem('zerohour_panic_sse_session_id', res.sseSessionId);
        
        fetchSessions();
        
        if (res.ready) {
          localStorage.setItem('zerohour_panic_flow_state', 'thinking');
          setMessages((prev) => [...prev, { role: 'assistant', content: `Got it! Let's generate a rescue plan for: ${res.summary}` }]);
          setSseLogs([]);
          setSseCompleted(false);
          setReceivedSubtasks([]);
          setDisplayedSubtasks([]);
          setFlowState('thinking');
        } else {
          localStorage.setItem('zerohour_panic_flow_state', 'chat');
          setMessages((prev) => [...prev, { role: 'assistant', content: res.question }]);
        }
      } else {
        const res = await api.replyPanic(sessionId, messageText, currentAttachment);
        setSseSessionId(res.sseSessionId);
        localStorage.setItem('zerohour_panic_sse_session_id', res.sseSessionId);
        
        if (res.ready) {
          localStorage.setItem('zerohour_panic_flow_state', 'thinking');
          setMessages((prev) => [...prev, { role: 'assistant', content: `Got it! Let's generate a rescue plan for: ${res.summary}` }]);
          setSseLogs([]);
          setSseCompleted(false);
          setReceivedSubtasks([]);
          setDisplayedSubtasks([]);
          setFlowState('thinking');
        } else {
          localStorage.setItem('zerohour_panic_flow_state', 'chat');
          setMessages((prev) => [...prev, { role: 'assistant', content: res.question }]);
        }
      }
    } catch (err) {
      console.error(err);
      setMessages((prev) => [...prev, { role: 'assistant', content: "Sorry, I had trouble planning. Let's try again." }]);
    } finally {
      setIsSending(false);
    }
  };

  const handleConfirmPlan = async (editedSubtasks) => {
    setFlowState('confirming_agents');
    try {
      await api.editPanicPlan(sessionId, editedSubtasks);
      await api.confirmPanicPlan(sessionId);
      
      const totalMinutes = editedSubtasks.reduce((sum, s) => sum + s.durationMinutes, 0);
      setFinalDeadline(new Date(Date.now() + totalMinutes * 60 * 1000));
      
      localStorage.removeItem('zerohour_panic_session_id');
      localStorage.removeItem('zerohour_panic_flow_state');
      localStorage.removeItem('zerohour_panic_sse_session_id');

      setFlowState('confirmed');
    } catch (e) {
      console.error(e);
      setFlowState('preview');
    }
  };

  // Countdown timer for done page
  const [countdown, setCountdown] = useState('');
  useEffect(() => {
    if (!finalDeadline) return;
    const interval = setInterval(() => {
      const diff = new Date(finalDeadline).getTime() - Date.now();
      if (diff <= 0) {
        setCountdown('00:00:00');
        clearInterval(interval);
        return;
      }
      const h = Math.floor(diff / (1000 * 60 * 60));
      const m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
      const s = Math.floor((diff % (1000 * 60)) / 1000);
      setCountdown(`${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`);
    }, 1000);
    return () => clearInterval(interval);
  }, [finalDeadline]);

  return (
    <div className="dashboard-layout bg-[#0D0D0D] animate-fadeIn">
      
      {/* Side Navigation Bar */}
      <nav className="hidden md:flex w-64 flex-shrink-0 h-screen sticky top-0 flex flex-col py-6 glass-panel z-40 border-r border-white/10 rounded-r-2xl">
        <div className="px-6 mb-8">
          <h1 className="font-display text-3xl font-black text-white tracking-tighter italic">ZEROHOUR</h1>
          <p className="font-mono text-[9px] text-[#FF453A] uppercase tracking-[0.3em] mt-1 opacity-80">COMMANDER_TERMINAL</p>
        </div>

        <div className="flex flex-col gap-1 flex-grow">
          <Link to="/dashboard" className="flex items-center gap-4 text-[#94a3b8] px-6 py-3 hover:bg-white/5 hover:text-white transition-all font-mono text-xs tracking-wider">
            <span className="material-symbols-outlined">dashboard</span>
            DASHBOARD
          </Link>
          <a className="flex items-center gap-4 text-[#94a3b8] px-6 py-3 hover:bg-white/5 hover:text-white transition-all font-mono text-xs tracking-wider" href="https://calendar.google.com" target="_blank" rel="noreferrer">
            <span className="material-symbols-outlined">calendar_today</span>
            CALENDAR
          </a>
          <Link to="/settings" className="flex items-center gap-4 text-[#94a3b8] px-6 py-3 hover:bg-white/5 hover:text-white transition-all font-mono text-xs tracking-wider">
            <span className="material-symbols-outlined">settings</span>
            SETTINGS
          </Link>
        </div>

        <div className="px-6 mt-auto pb-8">
          <button 
            onClick={handleAbort}
            className="w-full bg-[#FF453A] text-white py-4 rounded-xl font-black font-mono text-xs flex items-center justify-center gap-2 hover:brightness-110 active:scale-95 transition-all border border-white/20 shadow-lg shadow-[#FF453A]/20 uppercase tracking-widest text-center"
          >
            <span className="material-symbols-outlined text-sm">arrow_back</span>
            Abort Op
          </button>
        </div>
      </nav>

      {/* Middle History Sidebar */}
      <aside className="hidden lg:flex w-64 flex-shrink-0 h-screen sticky top-0 flex flex-col py-6 px-4 bg-[#121212]/30 border-r border-white/5 z-30 select-none">
        <div className="mb-4">
          <h3 className="font-mono text-[10px] text-[#FF453A] uppercase tracking-widest font-black flex items-center gap-2">
            <span className="material-symbols-outlined text-sm">history</span>
            Crisis Log History
          </h3>
        </div>

        <button 
          onClick={handleStartNewSession}
          className="w-full flex items-center justify-center gap-2 py-2 mb-4 bg-white/5 hover:bg-white/10 text-xs font-mono font-bold uppercase tracking-wider text-white border border-white/10 rounded-lg transition-all"
        >
          <span className="material-symbols-outlined text-xs">add</span>
          New Session
        </button>

        <div className="flex-grow flex flex-col gap-2 overflow-y-auto pr-1">
          {sessions.map((s) => {
            const isEditing = editingSessionId === s.id;
            const isSelected = sessionId === s.id;
            return (
              <div 
                key={s.id}
                onClick={() => !isEditing && loadSession(s.id)}
                className={`p-3 rounded-lg border text-left cursor-pointer transition-all flex flex-col gap-1 ${
                  isSelected 
                    ? 'bg-[#FF453A]/5 border-[#FF453A]/30 text-white' 
                    : 'bg-white/2 border-white/5 text-gray-400 hover:bg-white/5 hover:text-white'
                }`}
              >
                <div className="flex justify-between items-start gap-1">
                  {isEditing ? (
                    <div className="flex items-center gap-1 w-full">
                      <input 
                        type="text"
                        value={editTitle}
                        onChange={(e) => setEditTitle(e.target.value)}
                        className="font-mono text-[10px] bg-white/10 border border-white/20 rounded px-1 py-0.5 text-white w-full"
                        onClick={(e) => e.stopPropagation()}
                        onKeyDown={(e) => e.key === 'Enter' && handleRenameSession(s.id, editTitle)}
                        autoFocus
                      />
                      <button 
                        onClick={(e) => {
                          e.stopPropagation();
                          handleRenameSession(s.id, editTitle);
                        }}
                        className="p-1 text-emerald-400 hover:text-emerald-300 transition-colors flex-shrink-0"
                        title="Save rename"
                      >
                        <span className="material-symbols-outlined text-xs">check</span>
                      </button>
                    </div>
                  ) : (
                    <span className="font-mono text-[10px] font-bold truncate tracking-tight flex-grow">
                      {s.title || 'Untitled Session'}
                    </span>
                  )}

                  {!isEditing && isSelected && (
                    <div className="flex items-center gap-0.5 flex-shrink-0">
                      <button 
                        onClick={(e) => {
                          e.stopPropagation();
                          setEditingSessionId(s.id);
                          setEditTitle(s.title || '');
                        }}
                        className="p-1 hover:text-white text-gray-500 transition-colors"
                        title="Rename"
                      >
                        <span className="material-symbols-outlined text-xs">edit</span>
                      </button>
                      <button 
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDeleteSession(s.id);
                        }}
                        className="p-1 hover:text-[#FF453A] text-gray-500 transition-colors"
                        title="Delete"
                      >
                        <span className="material-symbols-outlined text-xs">delete</span>
                      </button>
                    </div>
                  )}
                </div>
                
                <span className="text-[8px] font-mono text-gray-600 uppercase">
                  {new Date(s.createdAt).toLocaleDateString(undefined, {month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'})}
                </span>
              </div>
            );
          })}
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-grow min-h-screen flex flex-col relative bg-[#0D0D0D]">
        
        {/* Panic warning banner */}
        <div className="w-full bg-[#FF453A]/90 backdrop-blur-md py-3 px-4 md:px-8 flex justify-between items-center z-30 shadow-xl">
          <div className="flex items-center gap-3">
            <span className="material-symbols-outlined text-white animate-pulse text-base">error</span>
            <h1 className="font-mono text-[10px] md:text-xs font-bold text-white tracking-[0.2em] uppercase">Panic Mode Active</h1>
          </div>
          <button 
            onClick={handleAbort}
            className="md:hidden bg-white/10 hover:bg-white/20 text-white font-mono text-[9px] font-bold px-3 py-1 rounded border border-white/20 uppercase tracking-wider transition-all animate-pulse"
          >
            Abort
          </button>
        </div>

        {targetTask && (
          <div className="w-full bg-white/5 border-b border-white/10 px-4 md:px-8 py-2 text-[10px] font-mono text-gray-400 flex items-center gap-2">
            <span className="material-symbols-outlined text-xs text-[#FF453A] animate-spin">sync</span>
            EMERGENCY RE-PLANNING FOR: <span className="text-white font-bold uppercase">{targetTask.title}</span>
          </div>
        )}

        <div className="flex-grow p-4 md:p-10 overflow-y-auto bg-transparent flex flex-col items-center">
          <div className="w-full max-w-[700px] pb-32">
            
            {/* Chatting Q&A Panel */}
            {flowState === 'chat' && (
              <div className="glass-panel p-4 md:p-6 flex flex-col h-[70vh] md:h-[550px] justify-between relative border-white/10 rounded-2xl bg-white/2">
                {/* Messages body */}
                <div className="flex flex-col gap-5 overflow-y-auto pr-2 mb-4">
                  {messages.map((m, idx) => {
                    const isAssistant = m.role === 'assistant';
                    return (
                      <div 
                        key={idx} 
                        className={`flex gap-3 items-start ${isAssistant ? 'justify-start' : 'flex-row-reverse justify-start'} animate-slide-up`}
                      >
                        {/* Icon */}
                        <div className={`flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center border ${
                          isAssistant 
                            ? 'bg-[#FF453A]/15 border-[#FF453A]/30 text-[#FF453A] shadow-[0_0_10px_rgba(255,69,58,0.25)]' 
                            : 'bg-white/5 border-white/10 text-[#94a3b8]'
                        }`}>
                          <span className="material-symbols-outlined text-base">
                            {isAssistant ? 'smart_toy' : 'person'}
                          </span>
                        </div>

                        {/* Speech Bubble */}
                        <div className="flex flex-col gap-1">
                          <div className={`p-4 rounded-2xl max-w-[85%] md:max-w-sm text-xs leading-relaxed ${
                            isAssistant 
                              ? 'bg-[#FF453A]/5 border border-[#FF453A]/20 text-[#e5e2e1] rounded-tl-none shadow-[0_0_15px_-5px_rgba(255,69,58,0.15)]' 
                              : 'bg-white/5 border border-white/10 text-white rounded-tr-none'
                          }`}>
                            <p className="whitespace-pre-line">{m.content}</p>
                          </div>
                          <span className={`text-[8px] font-mono text-gray-500 uppercase mt-1 ${isAssistant ? 'ml-1 text-left' : 'mr-1 text-right'}`}>
                            {isAssistant ? 'ZH Commander' : 'You'} • Just Now
                          </span>
                        </div>
                      </div>
                    );
                  })}
                  
                  {isSending && (
                    <div className="flex justify-start items-center gap-2 text-[#FF453A] text-[10px] font-mono font-bold tracking-wider p-2 bg-[#FF453A]/5 rounded-lg border border-[#FF453A]/10 max-w-[280px]">
                      <div className="thinking-dot" />
                      <div className="thinking-dot" />
                      <div className="thinking-dot" />
                      ZH AGENTS DEPLOYING PLAN...
                    </div>
                  )}
                  <div ref={messagesEndRef} />
                </div>

                <div className="flex flex-col gap-2 border-t border-white/5 pt-4">
                  {/* Attachment tag/pill */}
                  {attachment && (
                    <div className="flex items-center gap-2 bg-[#FF453A]/10 border border-[#FF453A]/20 px-3 py-1.5 rounded-lg text-[10px] font-mono text-white self-start animate-fade-in">
                      <Paperclip className="w-3 h-3 text-[#FF453A]" />
                      <span className="truncate max-w-[150px]">{attachment.fileName}</span>
                      <button 
                        type="button" 
                        onClick={removeAttachment}
                        className="text-gray-500 hover:text-white transition-colors"
                        title="Remove file"
                      >
                        <span className="material-symbols-outlined text-xs">close</span>
                      </button>
                    </div>
                  )}

                  {/* Input field */}
                  <form onSubmit={handleSendMessage} className="flex gap-3 items-center relative">
                    <input 
                      type="file" 
                      ref={fileInputRef} 
                      onChange={handleFileChange}
                      accept="image/*,application/pdf"
                      className="hidden"
                    />
                    
                    <button 
                      type="button"
                      onClick={() => fileInputRef.current?.click()}
                      className="p-3.5 bg-white/3 hover:bg-white/5 border border-white/10 rounded-xl text-gray-400 hover:text-white transition-all flex items-center justify-center flex-shrink-0"
                      title="Attach image or PDF"
                    >
                      <Paperclip className="w-4 h-4" />
                    </button>

                    <input 
                      type="text" 
                      value={userInput}
                      onChange={(e) => setUserInput(e.target.value)}
                      placeholder="Describe your deadline crisis (e.g. 'I have a math test in 2 hours')..."
                      className="input-field py-4 text-xs bg-white/3 flex-grow"
                      disabled={isSending}
                      required={!attachment}
                    />

                    <button 
                      type="submit" 
                      disabled={isSending || (!userInput.trim() && !attachment)}
                      className="btn btn-primary p-4 rounded-xl flex items-center justify-center flex-shrink-0"
                    >
                      <Send className="w-4 h-4 fill-white" />
                    </button>
                  </form>
                </div>
              </div>
            )}

            {/* Thinking Agent Stream View */}
            {flowState === 'thinking' && (
              <div className="animate-slide-up space-y-6">
                <AgentLogPanel logs={sseLogs} isCompleted={sseCompleted} />
                
                <div className="glass-panel p-8 text-center border-white/10 relative overflow-hidden bg-white/2">
                  <div className="absolute inset-0 bg-gradient-to-r from-[#FF453A]/5 via-transparent to-[#FF453A]/5 opacity-30 animate-pulse pointer-events-none" />
                  <Loader2 className="w-8 h-8 text-[#FF453A] animate-spin mx-auto mb-4" />
                  <h4 className="font-display font-bold uppercase text-white tracking-tight italic">Planning & Prioritization Active</h4>
                  
                  {/* Rotating Quote Display */}
                  <div className="h-8 flex items-center justify-center mt-2">
                    <p className="text-[11px] font-mono text-[#FF453A] uppercase tracking-wider transition-all duration-500 transform scale-100 opacity-90" key={activeQuoteIndex}>
                      {THINKING_QUOTES[activeQuoteIndex]}
                    </p>
                  </div>
                </div>

                {/* Incremental Plan Stream Panel */}
                {displayedSubtasks.length > 0 && (
                  <div className="glass-panel p-6 border-white/10 text-left bg-white/3 animate-fade-in max-w-[700px] w-full rounded-2xl border-l-4 border-l-[#FF453A]">
                    <div className="flex items-center justify-between border-b border-white/10 pb-3 mb-4">
                      <h4 className="font-mono text-xs text-[#FF453A] uppercase tracking-widest flex items-center gap-2 font-black">
                        <span className="material-symbols-outlined text-sm animate-pulse">terminal</span>
                        Incoming Rescue Plan (Bit-by-Bit Stream)
                      </h4>
                      <span className="font-mono text-[9px] text-gray-500 uppercase tracking-widest">
                        {displayedSubtasks.length}/{receivedSubtasks.length} subtasks
                      </span>
                    </div>

                    <div className="flex flex-col gap-3 font-mono text-[11px] max-h-[300px] overflow-y-auto pr-2">
                      {displayedSubtasks.map((sub, i) => {
                        if (!sub) return null;
                        const isLast = i === displayedSubtasks.length - 1;
                        return (
                          <div 
                            key={i} 
                            className={`flex justify-between items-center border-b border-white/5 pb-2 animate-slide-up ${
                              isLast ? 'text-white font-bold' : 'text-gray-300'
                            }`}
                          >
                            <div className="flex items-center gap-2">
                              <span className="text-[#FF453A] font-bold animate-pulse">&gt;</span>
                              <span>{sub.title}</span>
                            </div>
                            <div className="flex items-center gap-3">
                              <span className="text-gray-500">{sub.durationMinutes}m</span>
                              <span className={`text-[8px] px-1.5 py-0.5 rounded font-bold border ${
                                sub.priority === 'CRITICAL' ? 'bg-[#FF453A]/10 text-[#FF453A] border-[#FF453A]/20' :
                                sub.priority === 'HIGH' ? 'bg-[#F59E0B]/10 text-[#F59E0B] border-[#F59E0B]/20' :
                                'bg-blue-500/10 text-blue-400 border-blue-500/20'
                              }`}>
                                {sub.priority}
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Preview and Edit generated plan before locking in */}
            {flowState === 'preview' && (
              <div className="animate-slide-up">
                <PlanPreview 
                  key={sessionId || 'preview'}
                  initialSubtasks={generatedSubtasks} 
                  onConfirm={handleConfirmPlan}
                />
              </div>
            )}

            {/* Syncing agents with APIs state */}
            {flowState === 'confirming_agents' && (
              <div className="glass-panel p-12 text-center animate-slide-up border-[#FF453A]/20 bg-[#FF453A]/3">
                <Loader2 className="w-8 h-8 text-[#FF453A] animate-spin mx-auto mb-4" />
                <h4 className="font-display font-bold uppercase text-white tracking-tight italic">Scheduler & Nudge Agents Engaged</h4>
                <p className="text-[10px] font-mono text-gray-500 mt-2 uppercase tracking-wider">Booking Google Calendar blocks and launching in-app/Gmail monitors...</p>
              </div>
            )}

            {/* Confirmed / Done Page */}
            {flowState === 'confirmed' && (
              <div className="glass-panel p-10 text-center flex flex-col items-center gap-6 animate-slide-up border-[#FF453A]/20 bg-white/2">
                <div className="w-16 h-16 rounded-full bg-[#FF453A]/10 border border-[#FF453A]/20 flex items-center justify-center shadow-glow">
                  <span className="material-symbols-outlined text-3xl text-[#FF453A]">check_box</span>
                </div>

                <div>
                  <h2 className="font-display text-3xl font-black uppercase text-white tracking-tight italic">Crisis Managed!</h2>
                  <p className="text-xs text-gray-400 mt-2 max-w-md uppercase tracking-wider leading-relaxed">
                    Google Calendar events are secured consecutively from your schedule, and Gmail notifications are queued.
                  </p>
                </div>

                {/* Countdown timer */}
                <div className="p-5 bg-white/2 rounded-xl border border-white/5 w-full max-w-xs text-center">
                  <span className="text-[9px] font-mono text-gray-500 uppercase tracking-widest block mb-1">Time Remaining to Zero Hour</span>
                  <span className="font-mono text-3xl font-black text-[#FF453A] block countdown-pulse mt-1">
                    {countdown}
                  </span>
                </div>

                <div className="flex flex-col sm:flex-row gap-4 w-full justify-center mt-6">
                  <a 
                    href="https://calendar.google.com" 
                    target="_blank" 
                    rel="noreferrer" 
                    className="btn btn-secondary py-3 px-6 text-[10px] flex items-center justify-center gap-2"
                  >
                    <span className="material-symbols-outlined text-[#FF453A] text-sm">calendar_today</span> Open Google Calendar
                  </a>
                  <Link 
                    to="/dashboard" 
                    className="btn btn-primary py-3 px-6 text-[10px] flex items-center justify-center"
                  >
                    Go to Dashboard
                  </Link>
                </div>
              </div>
            )}

          </div>
        </div>

      </main>

      {/* Mobile Bottom Navigation Bar */}
      <nav className="fixed bottom-0 left-0 right-0 h-16 bg-[#0D0D0D]/95 backdrop-blur-md border-t border-white/10 z-40 flex justify-around items-center md:hidden px-4 shadow-[0_-10px_20px_rgba(0,0,0,0.5)]">
        <Link to="/dashboard" className="flex flex-col items-center gap-1 text-[#94a3b8] hover:text-white transition-all">
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
