/**
 * HARMONIA — WEB CLIENT & AUDIO INTELLIGENCE ENGINE
 * Real-time library management, audio analysis orchestration, and HTTP byte-range streaming.
 */

(function () {
  'use strict';

  // ── Global Application State ────────────────────────────────────────────────
  const state = {
    tracks: [],
    filteredTracks: [],
    selectedTrack: null,
    selectedFile: null,
    activeAnalysis: null,
    playingTrack: null,
    isPlaying: false,
    currentFilter: 'all',
    searchQuery: '',
    analysisPollingInterval: null,
  };

  // ── DOM References ──────────────────────────────────────────────────────────
  const elements = {
    // Header & Stats
    statsSummaryText: document.getElementById('statsSummaryText'),
    searchInput: document.getElementById('searchInput'),
    clearSearchBtn: document.getElementById('clearSearchBtn'),
    refreshLibraryBtn: document.getElementById('refreshLibraryBtn'),
    trackCountBadge: document.getElementById('trackCountBadge'),

    // Library Table & States
    tracksTable: document.getElementById('tracksTable'),
    tracksTableBody: document.getElementById('tracksTableBody'),
    emptyState: document.getElementById('emptyState'),
    emptyStateTitle: document.getElementById('emptyStateTitle'),
    emptyStateDesc: document.getElementById('emptyStateDesc'),
    filterChips: document.querySelectorAll('.filter-chip'),

    // Details Panel
    detailsPanel: document.getElementById('detailsPanel'),
    detailsPlaceholder: document.getElementById('detailsPlaceholder'),
    detailsContent: document.getElementById('detailsContent'),
    detailArtworkImg: document.getElementById('detailArtworkImg'),
    detailAmbientArt: document.getElementById('detailAmbientArt'),
    detailArtworkFallback: document.getElementById('detailArtworkFallback'),
    detailFormatBadge: document.getElementById('detailFormatBadge'),
    detailQualityBadge: document.getElementById('detailQualityBadge'),
    detailTitle: document.getElementById('detailTitle'),
    detailArtist: document.getElementById('detailArtist'),
    detailAlbum: document.getElementById('detailAlbum'),
    heroPlayBtn: document.getElementById('heroPlayBtn'),

    // Technical Specs
    specCodec: document.getElementById('specCodec'),
    specBitrate: document.getElementById('specBitrate'),
    specSampleRate: document.getElementById('specSampleRate'),
    specChannels: document.getElementById('specChannels'),
    specBitDepth: document.getElementById('specBitDepth'),
    specFileSize: document.getElementById('specFileSize'),
    specEncoder: document.getElementById('specEncoder'),

    // Audio Intelligence Section
    analysisStatusPill: document.getElementById('analysisStatusPill'),
    triggerAnalyzeBtn: document.getElementById('triggerAnalyzeBtn'),
    analyzeBtnText: document.getElementById('analyzeBtnText'),
    analysisTimerText: document.getElementById('analysisTimerText'),
    analysisUnrunNotice: document.getElementById('analysisUnrunNotice'),
    analysisFailedNotice: document.getElementById('analysisFailedNotice'),
    analysisErrorDesc: document.getElementById('analysisErrorDesc'),
    analysisMetricsGrid: document.getElementById('analysisMetricsGrid'),

    // Signal Metrics Elements
    metricLufs: document.getElementById('metricLufs'),
    lufsTargetComparison: document.getElementById('lufsTargetComparison'),
    lufsMeterFill: document.getElementById('lufsMeterFill'),
    metricTruePeak: document.getElementById('metricTruePeak'),
    tpMeterFill: document.getElementById('tpMeterFill'),
    metricLra: document.getElementById('metricLra'),
    metricCrest: document.getElementById('metricCrest'),
    metricClippingBadge: document.getElementById('metricClippingBadge'),
    metricClipSamples: document.getElementById('metricClipSamples'),
    metricLeadSilence: document.getElementById('metricLeadSilence'),
    metricTrailSilence: document.getElementById('metricTrailSilence'),
    provEngine: document.getElementById('provEngine'),
    provDuration: document.getElementById('provDuration'),

    // Bottom Player Dock
    audioElement: document.getElementById('audioElement'),
    playerThumbImg: document.getElementById('playerThumbImg'),
    playerThumbFallback: document.getElementById('playerThumbFallback'),
    playerTitle: document.getElementById('playerTitle'),
    playerArtist: document.getElementById('playerArtist'),
    prevBtn: document.getElementById('prevBtn'),
    playPauseBtn: document.getElementById('playPauseBtn'),
    playIcon: document.getElementById('playIcon'),
    pauseIcon: document.getElementById('pauseIcon'),
    nextBtn: document.getElementById('nextBtn'),
    currentTimeDisplay: document.getElementById('currentTimeDisplay'),
    totalDurationDisplay: document.getElementById('totalDurationDisplay'),
    progressBarWrap: document.getElementById('progressBarWrap'),
    bufferBar: document.getElementById('bufferBar'),
    progressBar: document.getElementById('progressBar'),
    seekSlider: document.getElementById('seekSlider'),
    muteBtn: document.getElementById('muteBtn'),
    volHighIcon: document.getElementById('volHighIcon'),
    volMuteIcon: document.getElementById('volMuteIcon'),
    volumeSlider: document.getElementById('volumeSlider'),

    // Toasts
    toastContainer: document.getElementById('toastContainer'),
  };

  // ── Helper: Format seconds to M:SS ──────────────────────────────────────────
  function formatTime(seconds) {
    if (isNaN(seconds) || seconds === null || seconds === undefined) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  }

  // ── Helper: Toast Notifications ─────────────────────────────────────────────
  function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    elements.toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      toast.style.transition = 'all 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 3500);
  }

  // ── API Layer ───────────────────────────────────────────────────────────────
  const api = {
    async getLibraryInfo() {
      const res = await fetch('/api/v1/info');
      if (!res.ok) throw new Error('Failed to fetch library info');
      return res.json();
    },

    async getTracks() {
      const res = await fetch('/api/v1/tracks?page=1&page_size=200');
      if (!res.ok) throw new Error('Failed to fetch tracks');
      const data = await res.json();
      return data.items || [];
    },

    async getTrackDetail(trackId) {
      const res = await fetch(`/api/v1/tracks/${trackId}`);
      if (!res.ok) throw new Error('Failed to fetch track details');
      return res.json();
    },

    async getFileAnalysis(fileId) {
      const res = await fetch(`/api/v1/files/${fileId}/analysis`);
      if (res.status === 404) return null;
      if (!res.ok) throw new Error('Failed to fetch audio analysis');
      return res.json();
    },

    async triggerFileAnalysis(fileId, force = false) {
      const res = await fetch(`/api/v1/files/${fileId}/analyze?force=${force}`, {
        method: 'POST',
      });
      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.detail || 'Failed to trigger audio analysis');
      }
      return res.json();
    },

    async search(query) {
      const res = await fetch(`/api/v1/search?q=${encodeURIComponent(query)}`);
      if (!res.ok) throw new Error('Search request failed');
      return res.json();
    },
  };

  // ── Library Data Loading & Filtering ────────────────────────────────────────
  async function loadLibrary() {
    try {
      // 1. Load high-level stats
      const info = await api.getLibraryInfo();
      elements.statsSummaryText.textContent = `${info.total_tracks} tracks • ${info.total_albums} albums • ${info.total_artists} artists`;

      // 2. Load track records
      state.tracks = await api.getTracks();
      applyFilters();
    } catch (err) {
      console.error('Error loading library:', err);
      elements.statsSummaryText.textContent = 'API Connected (0 tracks)';
      state.tracks = [];
      applyFilters();
    }
  }

  function applyFilters() {
    let list = [...state.tracks];

    // Search query filter
    if (state.searchQuery.trim()) {
      const q = state.searchQuery.toLowerCase();
      list = list.filter((t) =>
        (t.title && t.title.toLowerCase().includes(q)) ||
        (t.artist_name && t.artist_name.toLowerCase().includes(q)) ||
        (t.album_title && t.album_title.toLowerCase().includes(q)) ||
        (t.genre && t.genre.toLowerCase().includes(q))
      );
    }

    // Tab filter
    if (state.currentFilter === 'lossless') {
      list = list.filter((t) => t.is_lossless || (t.format && ['flac', 'wav', 'alac', 'aiff'].includes(t.format.toLowerCase())));
    } else if (state.currentFilter === 'analyzed') {
      // Filter tracks whose analysis has been completed
      list = list.filter((t) => t.analysis_status === 'COMPLETED');
    }

    state.filteredTracks = list;
    elements.trackCountBadge.textContent = `${list.length} track${list.length === 1 ? '' : 's'}`;
    renderTable();
  }

  // ── Render Library Table ────────────────────────────────────────────────────
  function renderTable() {
    elements.tracksTableBody.innerHTML = '';

    if (state.filteredTracks.length === 0) {
      elements.tracksTable.style.display = 'none';
      elements.emptyState.style.display = 'flex';
      if (state.searchQuery.trim()) {
        elements.emptyStateTitle.textContent = 'No matching tracks';
        elements.emptyStateDesc.textContent = `No search results matching "${state.searchQuery}". Try a different search term.`;
      } else {
        elements.emptyStateTitle.textContent = 'Your library is empty';
        elements.emptyStateDesc.innerHTML = 'Use <code class="cli-snippet">harmonia import &lt;music_dir&gt;</code> to scan local audio files.';
      }
      return;
    }

    elements.tracksTable.style.display = 'table';
    elements.emptyState.style.display = 'none';

    state.filteredTracks.forEach((track, index) => {
      const row = document.createElement('tr');
      row.className = 'track-row';
      row.dataset.trackId = track.id;

      if (state.selectedTrack && state.selectedTrack.id === track.id) {
        row.classList.add('selected');
      }
      if (state.playingTrack && state.playingTrack.id === track.id) {
        row.classList.add('playing');
      }

      // Artwork cell
      let artHtml = '';
      if (track.art_url) {
        artHtml = `<img class="row-thumb" src="${track.art_url}" alt="Art" loading="lazy" onerror="this.outerHTML='<div class=\\'row-thumb-fallback\\'>♫</div>'">`;
      } else {
        artHtml = `<div class="row-thumb-fallback"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg></div>`;
      }

      // Format Pill
      const isLossless = track.is_lossless || (track.format && ['flac', 'wav', 'alac', 'aiff'].includes(track.format.toLowerCase()));
      const formatStr = track.format ? track.format.toUpperCase() : 'AUDIO';
      const formatHtml = `<span class="format-pill ${isLossless ? 'lossless' : ''}">${formatStr}</span>`;

      // Status Pill
      let statusHtml = '<span class="status-pill idle">IDLE</span>';
      if (track.analysis_status === 'COMPLETED') {
        statusHtml = '<span class="status-pill completed">✓ ANALYZED</span>';
      } else if (track.analysis_status === 'RUNNING') {
        statusHtml = '<span class="status-pill running">⏳ RUNNING</span>';
      } else if (track.analysis_status === 'FAILED') {
        statusHtml = '<span class="status-pill failed">⚠️ FAILED</span>';
      }

      row.innerHTML = `
        <td class="track-num">${index + 1}</td>
        <td>${artHtml}</td>
        <td>
          <div class="title-cell">
            <span class="track-main-title">${escapeHtml(track.title || 'Untitled')}</span>
            <span class="track-main-artist">${escapeHtml(track.artist_name || 'Unknown Artist')}</span>
          </div>
        </td>
        <td class="album-cell">${escapeHtml(track.album_title || '—')}</td>
        <td class="time-cell">${track.duration_display || '-'}</td>
        <td style="text-align: center;">${formatHtml}</td>
        <td>${statusHtml}</td>
        <td style="text-align: center;">
          <button class="row-play-btn" title="Play Track" data-track-id="${track.id}">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <polygon points="5 3 19 12 5 21 5 3"></polygon>
            </svg>
          </button>
        </td>
      `;

      // Click row to select
      row.addEventListener('click', (e) => {
        if (e.target.closest('.row-play-btn')) return; // handled by play button listener
        selectTrack(track.id);
      });

      // Play button in row
      const playBtn = row.querySelector('.row-play-btn');
      playBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        selectAndPlayTrack(track.id);
      });

      // Double-click row to play
      row.addEventListener('dblclick', () => {
        selectAndPlayTrack(track.id);
      });

      elements.tracksTableBody.appendChild(row);
    });
  }

  // ── Track Selection & Detail Panel ──────────────────────────────────────────
  async function selectTrack(trackId) {
    try {
      // 1. Fetch full track details
      const detail = await api.getTrackDetail(trackId);
      state.selectedTrack = detail;
      state.selectedFile = detail.files && detail.files.length > 0 ? detail.files[0] : null;

      // Update table highlight
      document.querySelectorAll('.track-row').forEach((r) => {
        r.classList.toggle('selected', r.dataset.trackId === trackId);
      });

      // Populate Track Hero
      elements.detailTitle.textContent = detail.title || 'Untitled';
      elements.detailArtist.textContent = detail.artist_name || 'Unknown Artist';
      const yearStr = detail.year ? ` • ${detail.year}` : '';
      elements.detailAlbum.textContent = `${detail.album_title || 'Unknown Album'}${yearStr}`;

      // Artwork
      if (detail.art_url) {
        elements.detailArtworkImg.src = detail.art_url;
        elements.detailArtworkImg.style.display = 'block';
        elements.detailAmbientArt.style.backgroundImage = `url(${detail.art_url})`;
        elements.detailArtworkFallback.style.display = 'none';
      } else {
        elements.detailArtworkImg.style.display = 'none';
        elements.detailAmbientArt.style.backgroundImage = 'none';
        elements.detailArtworkFallback.style.display = 'flex';
      }

      // Badges
      const format = state.selectedFile ? state.selectedFile.format.toUpperCase() : 'AUDIO';
      elements.detailFormatBadge.textContent = format;

      const isLossless = state.selectedFile ? state.selectedFile.is_lossless : false;
      elements.detailQualityBadge.textContent = isLossless ? 'Lossless' : 'Compressed';
      elements.detailQualityBadge.className = `quality-badge ${isLossless ? 'lossless' : ''}`;

      // Technical Specs
      if (state.selectedFile) {
        const f = state.selectedFile;
        elements.specCodec.textContent = f.codec ? f.codec.toUpperCase() : '-';
        elements.specBitrate.textContent = f.bitrate ? `${f.bitrate} kbps` : '-';
        elements.specSampleRate.textContent = f.sample_rate ? `${f.sample_rate.toLocaleString()} Hz` : '-';
        elements.specChannels.textContent = f.channel_layout ? f.channel_layout : (f.channels ? `${f.channels} ch` : '-');
        elements.specBitDepth.textContent = f.bit_depth ? `${f.bit_depth}-bit` : '-';
        elements.specFileSize.textContent = f.file_size_display || '-';
        elements.specEncoder.textContent = f.encoder || '—';
      } else {
        elements.specCodec.textContent = '-';
        elements.specBitrate.textContent = '-';
        elements.specSampleRate.textContent = '-';
        elements.specChannels.textContent = '-';
        elements.specBitDepth.textContent = '-';
        elements.specFileSize.textContent = '-';
        elements.specEncoder.textContent = '—';
      }

      // Reveal Details content
      elements.detailsPlaceholder.style.display = 'none';
      elements.detailsContent.style.display = 'flex';

      // 2. Fetch Audio Intelligence for this physical file
      if (state.selectedFile) {
        await refreshAudioAnalysis(state.selectedFile.id);
      } else {
        renderAnalysisState(null);
      }
    } catch (err) {
      console.error('Error selecting track:', err);
      showToast('Failed to load track details', 'error');
    }
  }

  // ── Audio Intelligence Refresh & Rendering ──────────────────────────────────
  async function refreshAudioAnalysis(fileId) {
    try {
      const analysis = await api.getFileAnalysis(fileId);
      state.activeAnalysis = analysis;
      renderAnalysisState(analysis);

      // If status is QUEUED or RUNNING, poll until completed
      if (analysis && (analysis.status === 'PENDING' || analysis.status === 'RUNNING')) {
        startAnalysisPolling(fileId);
      } else {
        stopAnalysisPolling();
      }
    } catch (err) {
      console.error('Error fetching analysis:', err);
      renderAnalysisState(null);
    }
  }

  function renderAnalysisState(analysis) {
    if (!analysis) {
      // Not yet analyzed
      elements.analysisStatusPill.className = 'status-pill idle';
      elements.analysisStatusPill.textContent = 'NOT ANALYZED';
      elements.analyzeBtnText.textContent = 'Analyze Audio';
      elements.triggerAnalyzeBtn.disabled = false;
      elements.analysisTimerText.style.display = 'none';
      elements.analysisUnrunNotice.style.display = 'block';
      elements.analysisFailedNotice.style.display = 'none';
      elements.analysisMetricsGrid.style.display = 'none';
      return;
    }

    const status = analysis.status;

    if (status === 'PENDING' || status === 'RUNNING') {
      elements.analysisStatusPill.className = 'status-pill running';
      elements.analysisStatusPill.textContent = status === 'PENDING' ? 'QUEUED' : 'ANALYZING...';
      elements.analyzeBtnText.textContent = 'Analyzing...';
      elements.triggerAnalyzeBtn.disabled = true;
      elements.analysisTimerText.style.display = 'inline';
      elements.analysisUnrunNotice.style.display = 'none';
      elements.analysisFailedNotice.style.display = 'none';
      elements.analysisMetricsGrid.style.display = 'none';
    } else if (status === 'COMPLETED') {
      elements.analysisStatusPill.className = 'status-pill completed';
      elements.analysisStatusPill.textContent = '✓ COMPLETED';
      elements.analyzeBtnText.textContent = 'Re-analyze';
      elements.triggerAnalyzeBtn.disabled = false;
      elements.analysisTimerText.style.display = 'none';
      elements.analysisUnrunNotice.style.display = 'none';
      elements.analysisFailedNotice.style.display = 'none';
      elements.analysisMetricsGrid.style.display = 'grid';

      // Populate real measured signal values
      // Integrated LUFS
      const lufs = analysis.integrated_lufs;
      if (lufs !== null && lufs !== undefined) {
        elements.metricLufs.textContent = `${lufs.toFixed(1)}`;
        // Normalized visual meter bar (-36 to 0 LUFS)
        const lufsPct = Math.min(100, Math.max(0, ((lufs + 36) / 36) * 100));
        elements.lufsMeterFill.style.width = `${lufsPct}%`;
        const diff = lufs - (-14.0);
        elements.lufsTargetComparison.textContent = `${diff >= 0 ? '+' : ''}${diff.toFixed(1)} dB relative to -14 LUFS`;
      } else {
        elements.metricLufs.textContent = '—';
      }

      // True Peak
      const tp = analysis.true_peak_dbtp;
      if (tp !== null && tp !== undefined) {
        elements.metricTruePeak.textContent = `${tp.toFixed(1)}`;
        const tpPct = Math.min(100, Math.max(0, ((tp + 24) / 24) * 100));
        elements.tpMeterFill.style.width = `${tpPct}%`;
      } else {
        elements.metricTruePeak.textContent = '—';
      }

      // LRA
      elements.metricLra.textContent = analysis.loudness_range_lu !== null && analysis.loudness_range_lu !== undefined
        ? analysis.loudness_range_lu.toFixed(1)
        : '—';

      // Crest Factor
      elements.metricCrest.textContent = analysis.peak_to_loudness_db !== null && analysis.peak_to_loudness_db !== undefined
        ? analysis.peak_to_loudness_db.toFixed(1)
        : '—';

      // Clipping
      if (analysis.is_clipped) {
        elements.metricClippingBadge.className = 'clipping-badge clipped';
        elements.metricClippingBadge.textContent = 'CLIPPED';
        elements.metricClipSamples.textContent = `${analysis.clipping_samples || 'Peak'} samples exceeded 0 dBFS`;
      } else {
        elements.metricClippingBadge.className = 'clipping-badge clean';
        elements.metricClippingBadge.textContent = 'Clean';
        elements.metricClipSamples.textContent = '0 digital clipped samples';
      }

      // Silence Detection
      elements.metricLeadSilence.textContent = analysis.silence_start_sec !== null && analysis.silence_start_sec !== undefined
        ? `${analysis.silence_start_sec.toFixed(2)}s`
        : 'None';
      elements.metricTrailSilence.textContent = analysis.silence_end_sec !== null && analysis.silence_end_sec !== undefined
        ? `${analysis.silence_end_sec.toFixed(2)}s`
        : 'None';

      // Provenance
      elements.provEngine.textContent = `${analysis.analyzer_name || 'ffmpeg_ebur128'} (v${analysis.analyzer_version || '1.0'})`;
      elements.provDuration.textContent = analysis.execution_time_sec !== null && analysis.execution_time_sec !== undefined
        ? `${analysis.execution_time_sec.toFixed(2)}s`
        : '—';

    } else if (status === 'FAILED') {
      elements.analysisStatusPill.className = 'status-pill failed';
      elements.analysisStatusPill.textContent = '⚠️ FAILED';
      elements.analyzeBtnText.textContent = 'Retry Analysis';
      elements.triggerAnalyzeBtn.disabled = false;
      elements.analysisTimerText.style.display = 'none';
      elements.analysisUnrunNotice.style.display = 'none';
      elements.analysisFailedNotice.style.display = 'block';
      elements.analysisErrorDesc.textContent = analysis.error_message || 'Signal analysis failed during processing.';
      elements.analysisMetricsGrid.style.display = 'none';
    }
  }

  function startAnalysisPolling(fileId) {
    stopAnalysisPolling();
    elements.analysisTimerText.style.display = 'inline';
    let ticks = 0;
    elements.analysisTimerText.textContent = 'Analyzing...';

    state.analysisPollingInterval = setInterval(async () => {
      ticks += 1;
      elements.analysisTimerText.textContent = `Analyzing (${ticks}s)...`;
      try {
        const analysis = await api.getFileAnalysis(fileId);
        if (analysis && (analysis.status === 'COMPLETED' || analysis.status === 'FAILED')) {
          stopAnalysisPolling();
          state.activeAnalysis = analysis;
          renderAnalysisState(analysis);
          if (analysis.status === 'COMPLETED') {
            showToast(`Analysis completed for ${state.selectedTrack.title}: ${analysis.integrated_lufs?.toFixed(1)} LUFS`, 'success');
            // Refresh library table row state
            loadLibrary();
          } else {
            showToast(`Analysis failed: ${analysis.error_message || 'Unknown error'}`, 'error');
          }
        }
      } catch (e) {
        console.warn('Polling error:', e);
      }
    }, 1200);
  }

  function stopAnalysisPolling() {
    if (state.analysisPollingInterval) {
      clearInterval(state.analysisPollingInterval);
      state.analysisPollingInterval = null;
    }
  }

  // ── Trigger Analysis Handler ────────────────────────────────────────────────
  elements.triggerAnalyzeBtn.addEventListener('click', async () => {
    if (!state.selectedFile) {
      showToast('No audio file associated with track', 'error');
      return;
    }

    const fileId = state.selectedFile.id;
    const force = state.activeAnalysis && state.activeAnalysis.status === 'COMPLETED';

    try {
      elements.triggerAnalyzeBtn.disabled = true;
      elements.analyzeBtnText.textContent = 'Enqueuing...';
      const queued = await api.triggerFileAnalysis(fileId, force);
      showToast('Audio analysis queued in background worker', 'info');
      state.activeAnalysis = queued;
      renderAnalysisState(queued);
      startAnalysisPolling(fileId);
    } catch (err) {
      console.error('Error triggering analysis:', err);
      showToast(err.message || 'Failed to trigger analysis', 'error');
      elements.triggerAnalyzeBtn.disabled = false;
    }
  });

  // ── Audio Playback Engine (HTTP Range Stream) ───────────────────────────────
  async function selectAndPlayTrack(trackId) {
    await selectTrack(trackId);
    playCurrentSelectedTrack();
  }

  function playCurrentSelectedTrack() {
    if (!state.selectedTrack || !state.selectedFile) {
      showToast('Cannot play: No file available for this track', 'error');
      return;
    }

    const track = state.selectedTrack;
    const file = state.selectedFile;

    state.playingTrack = track;

    // Update Bottom Dock Info
    elements.playerTitle.textContent = track.title || 'Untitled';
    elements.playerArtist.textContent = track.artist_name || 'Unknown Artist';

    if (track.art_url) {
      elements.playerThumbImg.src = track.art_url;
      elements.playerThumbImg.style.display = 'block';
      elements.playerThumbFallback.style.display = 'none';
    } else {
      elements.playerThumbImg.style.display = 'none';
      elements.playerThumbFallback.style.display = 'flex';
    }

    // Set Audio Source to Secure Streaming Endpoint
    const streamUrl = `/api/v1/stream/${file.id}`;
    elements.audioElement.src = streamUrl;
    elements.audioElement.load();
    elements.audioElement.play().catch((err) => {
      console.error('Audio play error:', err);
      showToast('Audio playback could not start automatically', 'error');
    });

    // Highlight row in table
    document.querySelectorAll('.track-row').forEach((r) => {
      r.classList.toggle('playing', r.dataset.trackId === track.id);
    });
  }

  // Audio Element Event Listeners
  elements.audioElement.addEventListener('play', () => {
    state.isPlaying = true;
    elements.playIcon.style.display = 'none';
    elements.pauseIcon.style.display = 'block';
  });

  elements.audioElement.addEventListener('pause', () => {
    state.isPlaying = false;
    elements.playIcon.style.display = 'block';
    elements.pauseIcon.style.display = 'none';
  });

  elements.audioElement.addEventListener('timeupdate', () => {
    const current = elements.audioElement.currentTime;
    const duration = elements.audioElement.duration || 0;
    elements.currentTimeDisplay.textContent = formatTime(current);

    if (duration > 0) {
      const pct = (current / duration) * 100;
      elements.progressBar.style.width = `${pct}%`;
      elements.seekSlider.value = pct;
    }
  });

  elements.audioElement.addEventListener('loadedmetadata', () => {
    const duration = elements.audioElement.duration || 0;
    elements.totalDurationDisplay.textContent = formatTime(duration);
  });

  elements.audioElement.addEventListener('progress', () => {
    if (elements.audioElement.buffered.length > 0) {
      const bufferedEnd = elements.audioElement.buffered.end(elements.audioElement.buffered.length - 1);
      const duration = elements.audioElement.duration || 1;
      const pct = Math.min(100, (bufferedEnd / duration) * 100);
      elements.bufferBar.style.width = `${pct}%`;
    }
  });

  elements.audioElement.addEventListener('ended', () => {
    state.isPlaying = false;
    elements.playIcon.style.display = 'block';
    elements.pauseIcon.style.display = 'none';
    // Auto-advance to next track in library list
    playNextTrack();
  });

  // Play / Pause Toggle Button
  elements.playPauseBtn.addEventListener('click', togglePlayPause);
  elements.heroPlayBtn.addEventListener('click', () => {
    if (state.playingTrack && state.selectedTrack && state.playingTrack.id === state.selectedTrack.id) {
      togglePlayPause();
    } else {
      playCurrentSelectedTrack();
    }
  });

  function togglePlayPause() {
    if (!state.playingTrack) {
      if (state.selectedTrack) {
        playCurrentSelectedTrack();
      } else if (state.filteredTracks.length > 0) {
        selectAndPlayTrack(state.filteredTracks[0].id);
      }
      return;
    }

    if (elements.audioElement.paused) {
      elements.audioElement.play().catch(console.error);
    } else {
      elements.audioElement.pause();
    }
  }

  // Next & Previous Track Navigation
  elements.nextBtn.addEventListener('click', playNextTrack);
  elements.prevBtn.addEventListener('click', playPreviousTrack);

  function playNextTrack() {
    if (!state.playingTrack || state.filteredTracks.length === 0) return;
    const currentIndex = state.filteredTracks.findIndex((t) => t.id === state.playingTrack.id);
    const nextIndex = (currentIndex + 1) % state.filteredTracks.length;
    selectAndPlayTrack(state.filteredTracks[nextIndex].id);
  }

  function playPreviousTrack() {
    if (!state.playingTrack || state.filteredTracks.length === 0) return;
    // If playing for more than 3 seconds, replay current track
    if (elements.audioElement.currentTime > 3) {
      elements.audioElement.currentTime = 0;
      return;
    }
    const currentIndex = state.filteredTracks.findIndex((t) => t.id === state.playingTrack.id);
    const prevIndex = (currentIndex - 1 + state.filteredTracks.length) % state.filteredTracks.length;
    selectAndPlayTrack(state.filteredTracks[prevIndex].id);
  }

  // Seeking Scrub Bar (utilizes HTTP 206 Partial Content byte ranges)
  elements.seekSlider.addEventListener('input', (e) => {
    const pct = parseFloat(e.target.value);
    const duration = elements.audioElement.duration || 0;
    if (duration > 0) {
      elements.progressBar.style.width = `${pct}%`;
      elements.currentTimeDisplay.textContent = formatTime((pct / 100) * duration);
    }
  });

  elements.seekSlider.addEventListener('change', (e) => {
    const pct = parseFloat(e.target.value);
    const duration = elements.audioElement.duration || 0;
    if (duration > 0) {
      elements.audioElement.currentTime = (pct / 100) * duration;
    }
  });

  // Volume & Mute Controls
  elements.volumeSlider.addEventListener('input', (e) => {
    const vol = parseFloat(e.target.value);
    elements.audioElement.volume = vol;
    elements.audioElement.muted = (vol === 0);
    updateVolumeIcon();
  });

  elements.muteBtn.addEventListener('click', () => {
    elements.audioElement.muted = !elements.audioElement.muted;
    if (!elements.audioElement.muted && elements.audioElement.volume === 0) {
      elements.audioElement.volume = 0.5;
      elements.volumeSlider.value = 0.5;
    }
    updateVolumeIcon();
  });

  function updateVolumeIcon() {
    const isMuted = elements.audioElement.muted || elements.audioElement.volume === 0;
    elements.volHighIcon.style.display = isMuted ? 'none' : 'block';
    elements.volMuteIcon.style.display = isMuted ? 'block' : 'none';
  }

  // ── Search & Filter Listeners ───────────────────────────────────────────────
  let searchDebounceTimer = null;
  elements.searchInput.addEventListener('input', (e) => {
    clearTimeout(searchDebounceTimer);
    const query = e.target.value;
    state.searchQuery = query;
    elements.clearSearchBtn.style.display = query ? 'block' : 'none';

    searchDebounceTimer = setTimeout(() => {
      applyFilters();
    }, 200);
  });

  elements.clearSearchBtn.addEventListener('click', () => {
    elements.searchInput.value = '';
    state.searchQuery = '';
    elements.clearSearchBtn.style.display = 'none';
    applyFilters();
    elements.searchInput.focus();
  });

  elements.filterChips.forEach((chip) => {
    chip.addEventListener('click', () => {
      elements.filterChips.forEach((c) => c.classList.remove('active'));
      chip.classList.add('active');
      state.currentFilter = chip.dataset.filter;
      applyFilters();
    });
  });

  elements.refreshLibraryBtn.addEventListener('click', () => {
    showToast('Refreshing library data...', 'info');
    loadLibrary();
  });

  // ── Global Keyboard Shortcuts ───────────────────────────────────────────────
  window.addEventListener('keydown', (e) => {
    // Focus search on '/' or 'Ctrl+K'
    if ((e.key === '/' || (e.ctrlKey && e.key === 'k')) && document.activeElement !== elements.searchInput) {
      e.preventDefault();
      elements.searchInput.focus();
      return;
    }

    // Spacebar to toggle Play/Pause (when not typing in search)
    if (e.code === 'Space' && document.activeElement !== elements.searchInput) {
      e.preventDefault();
      togglePlayPause();
      return;
    }

    // Left/Right Arrow to seek ±5s
    if (e.code === 'ArrowLeft' && document.activeElement !== elements.searchInput) {
      e.preventDefault();
      elements.audioElement.currentTime = Math.max(0, elements.audioElement.currentTime - 5);
      return;
    }
    if (e.code === 'ArrowRight' && document.activeElement !== elements.searchInput) {
      e.preventDefault();
      const dur = elements.audioElement.duration || Infinity;
      elements.audioElement.currentTime = Math.min(dur, elements.audioElement.currentTime + 5);
      return;
    }
  });

  // ── HTML Sanitizer Helper ───────────────────────────────────────────────────
  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // ── Application Initialization ──────────────────────────────────────────────
  loadLibrary();
})();
