import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { Trash2, Loader2 } from 'lucide-react';
import { api } from '../services/api';
import { subscribeToAgentStream } from '../services/sse';
import AgentLogPanel from '../components/AgentLogPanel';

const PAGE_LOAD_TIME = Date.now();

export default function TaskDetail() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [task, setTask] = useState(null);
  const [subtasks, setSubtasks] = useState([]);
  const [calendarEvents, setCalendarEvents] = useState([]);
  const [nudges, setNudges] = useState([]);
  const [loading, setLoading] = useState(true);

  // Edit Mode States
  const [isEditing, setIsEditing] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editDeadline, setEditDeadline] = useState('');
  const [editDescription, setEditDescription] = useState('');

  // Countdown State
  const [timeLeft, setTimeLeft] = useState('');
  const [isCritical, setIsCritical] = useState(false);

  // SSE Scheduling state
  const [isConfirming, setIsConfirming] = useState(false);
  const [sseLogs, setSseLogs] = useState([]);
  const [sseCompleted, setSseCompleted] = useState(false);
  const [error, setError] = useState(null);

  // Subtask Customization States
  const [editingSubtaskId, setEditingSubtaskId] = useState(null);
  const [editSubtaskTitle, setEditSubtaskTitle] = useState('');
  const [editSubtaskDuration, setEditSubtaskDuration] = useState(30);
  const [editSubtaskPriority, setEditSubtaskPriority] = useState('MEDIUM');

  // Add Subtask Form State
  const [showAddSubtask, setShowAddSubtask] = useState(false);
  const [newSubtaskTitle, setNewSubtaskTitle] = useState('');
  const [newSubtaskDuration, setNewSubtaskDuration] = useState(30);
  const [newSubtaskPriority, setNewSubtaskPriority] = useState('MEDIUM');

  // Sync Calendar State
  const [isSyncingCalendar, setIsSyncingCalendar] = useState(false);

  // Set document title
  useEffect(() => {
    document.title = 'ZeroHour — Task Detail';
  }, []);

  // Fetch details inside useEffect as recommended to avoid synchronous updates warnings
  useEffect(() => {
    let active = true;
    const loadDetails = async () => {
      try {
        const data = await api.getTask(id);
        if (active) {
          setTask(data.task);
          setSubtasks(data.subtasks || []);
          setCalendarEvents(data.calendarEvents || []);
          setNudges(data.nudges || []);

          setEditTitle(data.task.title);
          setEditDeadline(
            data.task.deadline ? new Date(data.task.deadline).toISOString().substring(0, 16) : ''
          );
          setEditDescription(data.task.description || '');
        }
      } catch (err) {
        if (active) {
          console.error(err);
          setError('Failed to retrieve operation data. Please check connection.');
        }
      } finally {
        if (active) setLoading(false);
      }
    };
    
    loadDetails();
    return () => {
      active = false;
    };
  }, [id]);

  // Handle SSE when user clicks confirm task
  useEffect(() => {
    if (!isConfirming) return;

    const connection = subscribeToAgentStream(
      `confirm-${id}`,
      (data) => {
        setSseLogs((prev) => [...prev, data]);
        if (data.status === 'DONE' && data.agent === 'NudgeAgent') {
          setSseCompleted(true);
          // Reload details after confirmation is complete
          setTimeout(async () => {
            try {
              const res = await api.getTask(id);
              setTask(res.task);
              setSubtasks(res.subtasks || []);
              setCalendarEvents(res.calendarEvents || []);
              setNudges(res.nudges || []);
              setIsConfirming(false);
            } catch (err) {
              console.error(err);
            }
          }, 2000);
        }
      },
      (err) => {
        console.error(err);
      }
    );

    return () => connection.close();
  }, [isConfirming, id]);

  // Countdown timer trigger
  useEffect(() => {
    if (!task?.deadline) return;

    const updateTimer = () => {
      const diff = new Date(task.deadline).getTime() - Date.now();
      if (diff <= 0) {
        setTimeLeft('00:00:00');
        setIsCritical(true);
        return;
      }

      const h = Math.floor(diff / (1000 * 60 * 60));
      const m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
      const s = Math.floor((diff % (1000 * 60)) / 1000);

      const timeStr = `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
      setTimeLeft(timeStr);
      setIsCritical(diff < 60 * 60 * 1000);
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [task?.deadline]);

  const handleToggleSubtask = async (subtaskId, currentStatus) => {
    const nextStatus = currentStatus === 'DONE' ? 'PENDING' : 'DONE';
    setSubtasks(subtasks.map(s => s.id === subtaskId ? { ...s, status: nextStatus } : s));

    try {
      await api.toggleSubtask(id, subtaskId, nextStatus);
      const data = await api.getTask(id);
      setTask(data.task);
      setSubtasks(data.subtasks || []);
    } catch (e) {
      console.error(e);
      setSubtasks(subtasks.map(s => s.id === subtaskId ? { ...s, status: currentStatus } : s));
    }
  };

  const handleConfirmTask = async () => {
    // Reset SSE states before triggering streaming
    setSseLogs([]);
    setSseCompleted(false);
    setIsConfirming(true);
    try {
      await api.confirmTask(id);
    } catch (e) {
      console.error(e);
      setIsConfirming(false);
    }
  };

  const handleSyncCalendar = async () => {
    setIsSyncingCalendar(true);
    try {
      await api.syncCalendar(id);
      const data = await api.getTask(id);
      setTask(data.task);
      setSubtasks(data.subtasks || []);
      setCalendarEvents(data.calendarEvents || []);
      alert('Calendar synchronization completed successfully!');
    } catch (e) {
      console.error(e);
      alert('Failed to sync calendar: ' + e.message);
    } finally {
      setIsSyncingCalendar(false);
    }
  };

  const handleAddSubtask = async (e) => {
    e.preventDefault();
    if (!newSubtaskTitle.trim()) return;
    try {
      await api.createSubtask(id, {
        title: newSubtaskTitle,
        durationMinutes: parseInt(newSubtaskDuration),
        priority: newSubtaskPriority
      });
      const data = await api.getTask(id);
      setSubtasks(data.subtasks || []);
      setTask(data.task);
      
      setNewSubtaskTitle('');
      setNewSubtaskDuration(30);
      setNewSubtaskPriority('MEDIUM');
      setShowAddSubtask(false);
    } catch (err) {
      console.error(err);
      alert('Failed to add subtask: ' + err.message);
    }
  };

  const handleDeleteSubtask = async (subtaskId) => {
    if (!window.confirm('Are you sure you want to delete this subtask?')) return;
    try {
      await api.deleteSubtask(id, subtaskId);
      const data = await api.getTask(id);
      setSubtasks(data.subtasks || []);
      setCalendarEvents(data.calendarEvents || []);
      setTask(data.task);
    } catch (err) {
      console.error(err);
      alert('Failed to delete subtask: ' + err.message);
    }
  };

  const handleStartEditSubtask = (subtask) => {
    setEditingSubtaskId(subtask.id);
    setEditSubtaskTitle(subtask.title);
    setEditSubtaskDuration(subtask.durationMinutes);
    setEditSubtaskPriority(subtask.priority);
  };

  const handleSaveSubtask = async (subtaskId) => {
    if (!editSubtaskTitle.trim()) return;
    try {
      await api.updateSubtaskDetails(id, subtaskId, {
        title: editSubtaskTitle,
        durationMinutes: parseInt(editSubtaskDuration),
        priority: editSubtaskPriority
      });
      setEditingSubtaskId(null);
      const data = await api.getTask(id);
      setSubtasks(data.subtasks || []);
      setTask(data.task);
    } catch (err) {
      console.error(err);
      alert('Failed to update subtask: ' + err.message);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('zerohour_onboarded');
    window.location.href = api.logoutUrl;
  };

  const handleDeleteTask = async () => {
    if (!window.confirm('Are you sure you want to delete this task, calendar events, and scheduled nudges?')) return;
    try {
      await api.deleteTask(id);
      navigate('/dashboard');
    } catch (e) {
      console.error(e);
    }
  };

  const handleSaveEdit = async (e) => {
    e.preventDefault();
    try {
      const updated = await api.updateTask(id, {
        title: editTitle,
        deadline: new Date(editDeadline).toISOString(),
        description: editDescription
      });
      setTask(updated);
      setIsEditing(false);
      // Reload lists
      const data = await api.getTask(id);
      setSubtasks(data.subtasks || []);
      setCalendarEvents(data.calendarEvents || []);
      setNudges(data.nudges || []);
    } catch (err) {
      console.error('Failed to update task:', err);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[#0D0D0D] flex flex-col justify-center items-center gap-4">
        <Loader2 className="w-10 h-10 text-[#FF453A] animate-spin" />
        <p className="font-mono text-xs text-[#FF453A] uppercase tracking-[0.2em] animate-pulse">
          Retrieving tactical records...
        </p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-[#0D0D0D] flex flex-col justify-center items-center gap-6 p-4 animate-fadeIn">
        <div className="w-12 h-12 bg-red-950/40 border border-red-800 rounded-lg flex items-center justify-center text-[#FF453A]">
          <span className="material-symbols-outlined text-2xl">error</span>
        </div>
        <div className="text-center max-w-md">
          <h3 className="text-white font-bold text-lg mb-2">Sync Interrupted</h3>
          <p className="text-gray-400 text-xs font-mono uppercase tracking-wider leading-relaxed">{error}</p>
        </div>
        <Link to="/dashboard" className="btn btn-primary py-2 px-6 text-[10px] uppercase tracking-wider font-mono">
          Return to Dashboard
        </Link>
      </div>
    );
  }

  if (!task) {
    return (
      <div className="min-h-screen bg-[#0D0D0D] flex flex-col justify-center items-center gap-6 p-4 animate-fadeIn">
        <div className="w-12 h-12 bg-white/5 border border-white/10 rounded-lg flex items-center justify-center text-gray-400">
          <span className="material-symbols-outlined text-2xl">help_outline</span>
        </div>
        <div className="text-center max-w-md">
          <h3 className="text-white font-bold text-lg mb-2">Task Not Found</h3>
          <p className="text-gray-400 text-xs font-mono uppercase tracking-wider leading-relaxed">The requested action plan does not exist or has been deleted.</p>
        </div>
        <Link to="/dashboard" className="btn btn-primary py-2 px-6 text-[10px] uppercase tracking-wider font-mono">
          Return to Dashboard
        </Link>
      </div>
    );
  }

  if (isEditing) {
    // Keep it minimal when editing to avoid rendering complex elements
  }

  const isConfirmed = calendarEvents.length > 0;
  const completedCount = subtasks.filter(s => s.status === 'DONE').length;
  const totalCount = subtasks.length;
  const percentComplete = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;

  // Prioritization info
  const isCriticalPriority = task?.priority === 'CRITICAL';
  const labelText = isCriticalPriority ? 'MISSION CRITICAL' : `${task?.priority} PRIORITY`;
  const themeColorClass = 
    isCriticalPriority ? 'text-[#FF453A] border-[#FF453A]/40 bg-[#FF453A]/10' :
    task?.priority === 'HIGH' ? 'text-[#F59E0B] border-[#F59E0B]/30 bg-[#F59E0B]/10' :
    'text-blue-400 border-blue-500/20 bg-blue-500/10';

  // Nudge timeline items mapping
  const nudge24h = nudges.find(n => n.type === 'REMINDER_24H');
  const nudge6h = nudges.find(n => n.type === 'REMINDER_6H');
  const nudge1h = nudges.find(n => n.type === 'REMINDER_1H');

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
          <Link 
            to="/dashboard" 
            className="w-full bg-white/5 text-[#94a3b8] hover:text-white py-4 rounded-xl font-black font-mono text-xs flex items-center justify-center gap-2 hover:bg-white/10 transition-all border border-white/10 uppercase tracking-widest text-center"
          >
            <span className="material-symbols-outlined text-sm">arrow_back</span>
            Back to core
          </Link>
        </div>
      </nav>

      {/* Main Canvas */}
      <main className="flex-grow min-h-screen flex flex-col relative bg-[#0D0D0D]">
        
        {/* Header */}
        <header className="h-16 flex justify-between items-center px-6 md:px-12 border-b border-white/10 glass-panel z-30 sticky top-0">
          <div className="flex items-center gap-3">
            <Link to="/dashboard" className="p-2 rounded-lg hover:bg-white/5 text-[#94a3b8] hover:text-white transition-all mr-2">
              <span className="material-symbols-outlined text-xl">arrow_back</span>
            </Link>
            <span className="font-mono text-xs text-[#94a3b8] uppercase tracking-widest">Operation Detail</span>
          </div>

          <button 
            onClick={handleDeleteTask}
            className="btn btn-secondary border-[#FF453A]/20 hover:border-[#FF453A]/50 text-[#FF453A] py-2 px-4 text-[10px]"
          >
            <Trash2 className="w-3.5 h-3.5" /> Delete Task
          </button>
        </header>

        {/* Details Content */}
        <div className="flex-grow p-4 md:p-10 overflow-y-auto max-w-7xl mx-auto w-full grid grid-cols-1 lg:grid-cols-12 gap-6 pb-20">
          
          {/* SSE Thinking Panel */}
          {isConfirming && (
            <div className="lg:col-span-12">
              <AgentLogPanel logs={sseLogs} isCompleted={sseCompleted} />
            </div>
          )}

          {/* Header Section: Title & Countdown */}
          <section className="lg:col-span-12 flex flex-col md:flex-row justify-between items-start md:items-end gap-md mb-4 w-full">
            <div className="space-y-2 max-w-3xl">
              <div className="flex items-center gap-2">
                <span className={`px-2 py-0.5 border font-mono text-[9px] uppercase tracking-wider rounded font-bold ${themeColorClass}`}>
                  {labelText}
                </span>
                <span className="font-mono text-[9px] text-[#94a3b8] uppercase tracking-wider">
                  Status: {task?.status?.replace('_', ' ')}
                </span>
              </div>
              
              {isEditing ? (
                <form onSubmit={handleSaveEdit} className="flex flex-col gap-3 w-full bg-white/3 p-4 rounded-xl border border-white/5 mt-2">
                  <input 
                    type="text" 
                    value={editTitle}
                    onChange={(e) => setEditTitle(e.target.value)}
                    className="input-field py-2 text-xs"
                    required
                  />
                  <textarea 
                    value={editDescription}
                    onChange={(e) => setEditDescription(e.target.value)}
                    className="input-field h-20 text-xs py-2 resize-none"
                    placeholder="Task details..."
                  />
                  <input 
                    type="datetime-local" 
                    value={editDeadline}
                    onChange={(e) => setEditDeadline(e.target.value)}
                    className="input-field py-2 text-xs font-mono"
                    required
                  />
                  <div className="flex gap-2 justify-end mt-2">
                    <button type="button" onClick={() => setIsEditing(false)} className="btn btn-secondary py-1.5 px-3 text-[9px]">
                      <span className="material-symbols-outlined text-sm">close</span> Cancel
                    </button>
                    <button type="submit" className="btn btn-primary py-1.5 px-4 text-[9px] bg-teal-600 hover:bg-teal-700">
                      <span className="material-symbols-outlined text-sm">check</span> Save Changes
                    </button>
                  </div>
                </form>
              ) : (
                <h1 className="font-display font-black text-3xl md:text-4xl text-white uppercase italic tracking-tight leading-tight">
                  {task?.title}
                </h1>
              )}
            </div>
            
            <div className="flex flex-col items-start md:items-end min-w-[200px]">
              <span className="font-mono text-[9px] text-gray-500 uppercase tracking-widest mb-1">Deadline Countdown</span>
              <div className={`font-mono text-4xl font-bold ${isCritical ? 'text-[#FF453A] countdown-pulse' : 'text-white'}`}>
                {timeLeft}
              </div>
            </div>
          </section>

          {/* Action Bar */}
          {!isEditing && (
            <section className="lg:col-span-12 flex flex-wrap gap-3 pb-6 border-b border-white/10 w-full">
              <button 
                onClick={() => setIsEditing(true)}
                className="flex items-center gap-2 px-6 py-2.5 bg-white/5 hover:bg-white/10 border border-white/10 text-xs rounded-lg transition-all active:scale-95 text-white"
              >
                <span className="material-symbols-outlined text-sm">edit</span>
                <span>Edit Details</span>
              </button>

              {!isConfirmed && (
                <button 
                  onClick={handleConfirmTask}
                  disabled={isConfirming}
                  className="flex items-center gap-2 px-6 py-2.5 bg-[#14B8A6] text-black hover:brightness-110 rounded-lg transition-all active:scale-95 font-bold text-xs shadow-[0_0_20px_rgba(20,184,166,0.25)]"
                >
                  <span className="material-symbols-outlined text-sm">play_arrow</span>
                  <span>{isConfirming ? 'Processing...' : 'Push to Calendar'}</span>
                </button>
              )}

              <Link 
                to={`/panic?taskId=${id}`}
                className="flex items-center gap-2 px-6 py-2.5 bg-[#FF453A]/10 hover:bg-[#FF453A]/20 border border-[#FF453A]/20 text-xs rounded-lg transition-all active:scale-95 text-[#FF453A]"
              >
                <span className="material-symbols-outlined text-sm">emergency</span>
                <span>Trigger Crisis Re-Plan (Panic)</span>
              </Link>
            </section>
          )}

          {/* Left Column: Subtasks & Calendar details */}
          <div className="lg:col-span-8 space-y-6">
            
            {/* Subtasks checklist */}
            <div className="glass-panel p-6 border-white/10 bg-white/2">
              <div className="flex justify-between items-center mb-4">
                <h3 className="font-display text-sm font-bold uppercase tracking-wider text-white">Execution Sequence</h3>
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => setShowAddSubtask(!showAddSubtask)}
                    className="flex items-center gap-1 px-3 py-1 bg-white/5 hover:bg-white/10 text-white font-mono text-[9px] rounded-lg border border-white/10 uppercase tracking-widest transition-all"
                  >
                    <span className="material-symbols-outlined text-[10px]">add</span>
                    Add Step
                  </button>
                  <span className="font-mono text-[10px] text-[#14B8A6] font-bold">
                    {percentComplete}% COMPLETE
                  </span>
                </div>
              </div>

              {showAddSubtask && (
                <form onSubmit={handleAddSubtask} className="mb-6 p-4 rounded-xl border border-white/5 bg-white/2 flex flex-col gap-3 animate-fadeIn">
                  <h4 className="text-[10px] font-mono uppercase tracking-wider text-white font-bold">Add Manual Step</h4>
                  <div className="flex flex-col gap-1">
                    <label className="text-[8px] font-mono uppercase tracking-widest text-gray-500">Step Title</label>
                    <input 
                      type="text" 
                      placeholder="e.g. Write Introduction Section"
                      value={newSubtaskTitle}
                      onChange={(e) => setNewSubtaskTitle(e.target.value)}
                      className="input-field py-2 text-xs"
                      required
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="flex flex-col gap-1">
                      <label className="text-[8px] font-mono uppercase tracking-widest text-gray-500">Duration (Minutes)</label>
                      <input 
                        type="number" 
                        value={newSubtaskDuration}
                        onChange={(e) => setNewSubtaskDuration(parseInt(e.target.value) || 30)}
                        className="input-field py-2 text-xs"
                        min="1"
                      />
                    </div>
                    <div className="flex flex-col gap-1">
                      <label className="text-[8px] font-mono uppercase tracking-widest text-gray-500">Priority</label>
                      <select 
                        value={newSubtaskPriority}
                        onChange={(e) => setNewSubtaskPriority(e.target.value)}
                        className="input-field py-2 text-xs bg-[#13151a]"
                      >
                        <option value="CRITICAL">CRITICAL</option>
                        <option value="HIGH">HIGH</option>
                        <option value="MEDIUM">MEDIUM</option>
                        <option value="LOW">LOW</option>
                      </select>
                    </div>
                  </div>
                  <div className="flex gap-2 justify-end mt-1">
                    <button 
                      type="button" 
                      onClick={() => setShowAddSubtask(false)}
                      className="px-3 py-1.5 rounded-lg bg-white/5 text-[9px] font-mono uppercase tracking-wider text-gray-400 hover:bg-white/10"
                    >
                      Cancel
                    </button>
                    <button 
                      type="submit"
                      className="px-3 py-1.5 rounded-lg bg-[#14B8A6]/20 border border-[#14B8A6]/30 text-[9px] font-mono uppercase tracking-wider text-[#14B8A6] hover:bg-[#14B8A6]/30 font-bold"
                    >
                      Add Step
                    </button>
                  </div>
                </form>
              )}
              
              {/* Progress bar */}
              <div className="w-full h-1.5 bg-white/5 rounded-full mb-6 overflow-hidden">
                <div 
                  className="h-full bg-[#14B8A6] transition-all duration-500 shadow-[0_0_8px_rgba(20,184,166,0.6)]" 
                  style={{ width: `${percentComplete}%` }}
                />
              </div>

              <div className="flex flex-col gap-2">
                {subtasks.length === 0 ? (
                  <p className="font-mono text-[10px] text-gray-500 py-4 text-center uppercase tracking-widest">
                    No tactical units generated.
                  </p>
                ) : (
                  subtasks.map((s) => {
                    const isDone = s.status === 'DONE';
                    if (editingSubtaskId === s.id) {
                      return (
                        <div key={s.id} className="flex flex-col gap-3 p-4 rounded-xl border border-[#14B8A6]/30 bg-white/2 animate-fadeIn">
                          <div className="flex flex-col gap-1">
                            <label className="text-[8px] font-mono uppercase tracking-widest text-[#14B8A6]">Edit Step Title</label>
                            <input 
                              type="text" 
                              value={editSubtaskTitle}
                              onChange={(e) => setEditSubtaskTitle(e.target.value)}
                              className="input-field py-2 text-xs"
                            />
                          </div>
                          <div className="grid grid-cols-2 gap-3">
                            <div className="flex flex-col gap-1">
                              <label className="text-[8px] font-mono uppercase tracking-widest text-[#14B8A6]">Duration (Mins)</label>
                              <input 
                                type="number" 
                                value={editSubtaskDuration}
                                onChange={(e) => setEditSubtaskDuration(parseInt(e.target.value) || 0)}
                                className="input-field py-2 text-xs"
                              />
                            </div>
                            <div className="flex flex-col gap-1">
                              <label className="text-[8px] font-mono uppercase tracking-widest text-[#14B8A6]">Priority</label>
                              <select
                                value={editSubtaskPriority}
                                onChange={(e) => setEditSubtaskPriority(e.target.value)}
                                className="input-field py-2 text-xs bg-[#13151a]"
                              >
                                <option value="CRITICAL">CRITICAL</option>
                                <option value="HIGH">HIGH</option>
                                <option value="MEDIUM">MEDIUM</option>
                                <option value="LOW">LOW</option>
                              </select>
                            </div>
                          </div>
                          <div className="flex gap-2 justify-end mt-1">
                            <button 
                              type="button"
                              onClick={() => setEditingSubtaskId(null)}
                              className="px-3 py-1.5 rounded-lg bg-white/5 text-[9px] font-mono uppercase tracking-wider text-gray-400 hover:bg-white/10"
                            >
                              Cancel
                            </button>
                            <button 
                              type="button"
                              onClick={() => handleSaveSubtask(s.id)}
                              className="px-3 py-1.5 rounded-lg bg-[#14B8A6]/20 border border-[#14B8A6]/30 text-[9px] font-mono uppercase tracking-wider text-[#14B8A6] hover:bg-[#14B8A6]/30"
                            >
                              Save
                            </button>
                          </div>
                        </div>
                      );
                    }

                    return (
                      <div 
                        key={s.id} 
                        className={`group relative flex items-center justify-between p-3.5 rounded-xl border border-white/5 hover:bg-white/2 transition-all ${
                          isDone ? 'opacity-50' : ''
                        }`}
                      >
                        <div className="flex items-center gap-3 w-[70%]">
                          <input 
                            type="checkbox"
                            checked={isDone}
                            onChange={() => handleToggleSubtask(s.id, s.status)}
                            className="w-4 h-4 rounded border-gray-600 bg-transparent text-[#14B8A6] focus:ring-[#14B8A6] focus:ring-offset-0 cursor-pointer accent-[#14B8A6]"
                          />
                          <span className={`text-xs ${isDone ? 'line-through text-gray-500' : 'text-gray-200'}`}>
                            {s.title}
                          </span>
                        </div>

                        <div className="flex items-center gap-3">
                          <span className="text-[10px] font-mono text-gray-500">{s.durationMinutes}M</span>
                          <span className={`badge text-[8px] px-1.5 py-0.5 rounded ${
                            s.priority === 'CRITICAL' ? 'badge-critical' :
                            s.priority === 'HIGH' ? 'badge-high' :
                            s.priority === 'MEDIUM' ? 'badge-medium' : 'badge-low'
                          }`}>
                            {s.priority}
                          </span>
                          
                          <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button
                              type="button"
                              onClick={(e) => { e.preventDefault(); handleStartEditSubtask(s); }}
                              className="p-1 hover:bg-white/10 rounded text-gray-400 hover:text-white flex items-center justify-center"
                              title="Edit Step"
                            >
                              <span className="material-symbols-outlined text-[14px]">edit</span>
                            </button>
                            <button
                              type="button"
                              onClick={(e) => { e.preventDefault(); handleDeleteSubtask(s.id); }}
                              className="p-1 hover:bg-white/10 rounded text-gray-400 hover:text-[#FF453A] flex items-center justify-center"
                              title="Delete Step"
                            >
                              <span className="material-symbols-outlined text-[14px]">delete</span>
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            {/* Google Calendar sync status */}
            <div className="glass-panel p-6 border-white/10 overflow-hidden relative bg-white/2">
              <div className="absolute top-4 right-4 opacity-5 pointer-events-none">
                <span className="material-symbols-outlined text-7xl text-white">event_available</span>
              </div>
              <div className="flex items-center justify-between gap-2 mb-4 text-[#14B8A6]">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined">calendar_month</span>
                  <h3 className="font-display text-sm font-bold uppercase tracking-wider text-white">Google Calendar Blocks</h3>
                </div>
                {isConfirmed && (
                  <button
                    onClick={handleSyncCalendar}
                    disabled={isSyncingCalendar}
                    className="flex items-center gap-1 px-3 py-1 bg-white/5 hover:bg-white/10 text-white font-mono text-[9px] rounded-lg border border-white/10 uppercase tracking-widest transition-all"
                  >
                    {isSyncingCalendar ? (
                      <>
                        <Loader2 className="w-2.5 h-2.5 animate-spin" />
                        Syncing...
                      </>
                    ) : (
                      <>
                        <span className="material-symbols-outlined text-[10px]">sync</span>
                        Sync Calendar
                      </>
                    )}
                  </button>
                )}
              </div>
              
              {isConfirmed ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {calendarEvents.map((evt, idx) => {
                    const start = new Date(evt.startTime);
                    const end = new Date(evt.endTime);
                    const isPassed = end.getTime() < PAGE_LOAD_TIME;
                    
                    return (
                      <div 
                        key={evt.id} 
                        className={`p-4 bg-white/3 border-l-4 rounded-r-lg hover:bg-white/5 transition-colors ${
                          isPassed ? 'border-gray-600 opacity-60' : 'border-[#14B8A6]'
                        }`}
                      >
                        <div className="flex justify-between items-start mb-2">
                          <span className="font-mono text-[9px] text-[#14B8A6] font-bold">
                            {start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} — {end.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </span>
                          <a 
                            href={`https://calendar.google.com/calendar/r/eventedit/${evt.googleEventId}`} 
                            target="_blank" 
                            rel="noreferrer"
                            className="text-[#94a3b8] hover:text-white"
                          >
                            <span className="material-symbols-outlined text-xs">open_in_new</span>
                          </a>
                        </div>
                        <p className="text-xs font-bold text-gray-200 line-clamp-1">{subtasks[idx]?.title || task?.title}</p>
                        <p className="text-[9px] font-mono text-gray-500 mt-1 uppercase">Attributed to ZeroHour</p>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="p-4 bg-white/2 border-l-4 border-amber-500 rounded-r-lg text-xs text-amber-500/80">
                  Plan pending confirmation. Google Calendar sync requires locking the plan.
                </div>
              )}
            </div>

          </div>

          {/* Right Column: Nudge schedule timeline */}
          <div className="lg:col-span-4 space-y-6">
            
            {/* Nudge schedule */}
            <div className="glass-panel p-6 border-white/10 bg-white/2">
              <h3 className="font-display text-sm font-bold uppercase tracking-wider text-white mb-6">Nudge Monitor</h3>
              <div className="relative space-y-8 before:absolute before:left-3 before:top-2 before:bottom-2 before:w-px before:bg-white/10">
                
                {/* 24h Threshold */}
                <div className="relative pl-10 flex flex-col">
                  {nudge24h?.sent ? (
                    <div className="absolute left-0 w-6 h-6 rounded-full bg-[#14B8A6] flex items-center justify-center ring-4 ring-[#0D0D0D] z-10 shadow-[0_0_10px_rgba(20,184,166,0.4)]">
                      <span className="material-symbols-outlined text-black text-sm font-bold">check</span>
                    </div>
                  ) : (
                    <div className={`absolute left-0 w-6 h-6 rounded-full border-2 flex items-center justify-center ring-4 ring-[#0D0D0D] z-10 bg-[#0d0d0d] ${
                      isConfirmed ? 'border-amber-500 animate-pulse' : 'border-white/10'
                    }`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${isConfirmed ? 'bg-amber-500' : 'bg-white/10'}`}></span>
                    </div>
                  )}
                  <span className={`font-mono text-[9px] font-bold ${nudge24h?.sent ? 'text-[#14B8A6]' : isConfirmed ? 'text-amber-500' : 'text-gray-500'}`}>
                    24H BEFORE DEADLINE
                  </span>
                  <span className="text-[10px] text-gray-500 mt-1 uppercase tracking-wide leading-relaxed">
                    {nudge24h?.sent ? 'Initial prep reminder sent via Gmail.' : 'Pending: Status trigger.'}
                  </span>
                </div>

                {/* 6h Threshold */}
                <div className="relative pl-10 flex flex-col">
                  {nudge6h?.sent ? (
                    <div className="absolute left-0 w-6 h-6 rounded-full bg-[#14B8A6] flex items-center justify-center ring-4 ring-[#0D0D0D] z-10 shadow-[0_0_10px_rgba(20,184,166,0.4)]">
                      <span className="material-symbols-outlined text-black text-sm font-bold">check</span>
                    </div>
                  ) : (
                    <div className={`absolute left-0 w-6 h-6 rounded-full border-2 flex items-center justify-center ring-4 ring-[#0D0D0D] z-10 bg-[#0d0d0d] ${
                      isConfirmed ? 'border-amber-500 animate-pulse' : 'border-white/10'
                    }`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${isConfirmed ? 'bg-amber-500' : 'bg-white/10'}`}></span>
                    </div>
                  )}
                  <span className={`font-mono text-[9px] font-bold ${nudge6h?.sent ? 'text-[#14B8A6]' : isConfirmed ? 'text-amber-500' : 'text-gray-500'}`}>
                    6H BEFORE DEADLINE
                  </span>
                  <span className="text-[10px] text-gray-500 mt-1 uppercase tracking-wide leading-relaxed">
                    {nudge6h?.sent ? 'Middle progress monitor sent via Gmail + App.' : 'Pending: Status trigger.'}
                  </span>
                </div>

                {/* 1h Threshold */}
                <div className="relative pl-10 flex flex-col">
                  {nudge1h?.sent ? (
                    <div className="absolute left-0 w-6 h-6 rounded-full bg-[#14B8A6] flex items-center justify-center ring-4 ring-[#0D0D0D] z-10 shadow-[0_0_10px_rgba(20,184,166,0.4)]">
                      <span className="material-symbols-outlined text-black text-sm font-bold">check</span>
                    </div>
                  ) : (
                    <div className={`absolute left-0 w-6 h-6 rounded-full border-2 flex items-center justify-center ring-4 ring-[#0D0D0D] z-10 bg-[#0d0d0d] ${
                      isConfirmed ? 'border-amber-500 animate-pulse' : 'border-white/10'
                    }`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${isConfirmed ? 'bg-amber-500' : 'bg-white/10'}`}></span>
                    </div>
                  )}
                  <span className={`font-mono text-[9px] font-bold ${nudge1h?.sent ? 'text-[#14B8A6]' : isConfirmed ? 'text-amber-500' : 'text-gray-500'}`}>
                    1H BEFORE DEADLINE
                  </span>
                  <span className="text-[10px] text-gray-500 mt-1 uppercase tracking-wide leading-relaxed">
                    {nudge1h?.sent ? 'Final crisis threshold alert sent via Gmail + App.' : 'Pending: Status trigger.'}
                  </span>
                </div>

              </div>
            </div>

            {/* Assigned agents metadata */}
            <div className="glass-panel p-6 border-white/10 space-y-4 bg-white/2 text-xs">
              <div>
                <span className="font-mono text-[9px] text-gray-500 block mb-2 uppercase tracking-widest font-bold">Deploying Agents</span>
                <div className="flex -space-x-2">
                  <div className="w-10 h-10 rounded-full border-2 border-[#0D0D0D] bg-[#FF453A] flex items-center justify-center font-bold text-white shadow-lg text-[9px]">AI</div>
                  <div className="w-10 h-10 rounded-full border-2 border-[#0D0D0D] bg-white/10 flex items-center justify-center text-[#94a3b8] font-bold shadow-lg text-[9px]">ZH</div>
                </div>
              </div>
              
              <div className="pt-3 border-t border-white/5 font-mono text-[9px] text-[#94a3b8] tracking-widest font-bold">
                <span className="text-gray-500 block mb-1 uppercase">Task Identification</span>
                <span className="text-white select-all">{task?.id}</span>
              </div>
            </div>

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
