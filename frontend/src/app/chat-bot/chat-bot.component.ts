import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MsalBroadcastService, MsalService } from '@azure/msal-angular';
import { InteractionStatus } from '@azure/msal-browser';
import { filter, Subject, take, takeUntil } from 'rxjs';

// Message blueprint — every chat bubble (user or assistant) follows this structure
interface Message {
  role: 'user' | 'assistant';
  content: string;
  followUps?: string[];
  feedback?: 'up' | 'down' | null;
  id?: string;
  citations?: { fileName: string; pageNumber: number; docPath: string }[];
}

// Blueprint for follow-up questions returned by the backend
type FollowupsResponse = { followUps: string[] };

import { PdfqaService, AskStructuredResponse } from '../services/pdfqa.service';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-bot.component.html',
  styleUrls: ['./chat-bot.component.css']
})
export class ChatBotComponent implements AfterViewChecked, OnInit, OnDestroy {

  // References to HTML elements
  @ViewChild('chatContainer') private chatContainer!: ElementRef;
  @ViewChild('chatInput') private chatInputRef!: ElementRef<HTMLTextAreaElement>;

  // Current chat messages shown on screen
  messages: Message[] = [];
  userInput = '';
  loading = false;
  menuOpen = true;
  hasSubmittedOnce = false;
  copiedIndex: number | null = null;

  // NAVEEN - chatHistory holds the last 10 chats fetched from Azure DB for the sidebar
  // employeeId is the logged-in employee's email (e.g. naveen.kshirasagar@acs.nyc.gov)
  chatHistory: any[] = [];
  employeeId: string = '';
  private readonly destroying$ = new Subject<void>();

  constructor(
    private http: HttpClient,
    private router: Router,
    private pdfqa: PdfqaService,
    private authService: MsalService,
    private msalBroadcast: MsalBroadcastService
  ) {}

  // WORKFLOW STEP 1 — When the page loads:
  // 1. Wait for MSAL auth to fully complete (avoids race condition where account is null)
  // 2. Get the logged-in employee's email from Microsoft login (MSAL)
  // 3. Call loadChatHistory() to fetch their recent chats from Azure DB
  ngOnInit(): void {
    this.msalBroadcast.inProgress$
      .pipe(
        filter(status => status === InteractionStatus.None),
        take(1),
        takeUntil(this.destroying$)
      )
      .subscribe(() => {
        const accounts = this.authService.instance.getAllAccounts();
        if (accounts.length > 0) {
          this.authService.instance.setActiveAccount(accounts[0]);
        }
        const account = this.authService.instance.getActiveAccount();
        if (account?.username) {
          this.employeeId = account.username;
          this.loadChatHistory();
        }
      });
  }

  ngOnDestroy(): void {
    this.destroying$.next();
    this.destroying$.complete();
  }

  // WORKFLOW STEP 2 — Fetch recent chats from Azure DB
  // Calls: GET http://localhost:8081/api/chatlogs/{employeeId}
  // Backend filters chats by employeeId and returns last 10 ordered by newest first
  // Result is stored in chatHistory and displayed in the sidebar
  // NAVEEN
  loadChatHistory(): void {
    this.http.get<any[]>(`http://localhost:8081/api/chatlogs/${this.employeeId}`)
      .subscribe({
        next: (data) => { this.chatHistory = data; },
        error: (err) => { console.warn('Could not load chat history:', err); }
      });
  }

  // WORKFLOW STEP 3 — New Chat button clicked
  // Clears the current messages on screen
  // Does NOT delete anything from Azure DB — history stays in sidebar
  // NAVEEN
  newChat(): void {
    this.messages = [];
    this.hasSubmittedOnce = false;
    this.menuOpen = false;
  }

  // WORKFLOW STEP 3b — User clicks a recent chat from the sidebar
  // Loads that chat's question and answer into the main chat area
  // Does NOT make any API call — data already exists in chatHistory[]
  // NAVEEN
  loadChat(chat: any): void {
    this.messages = [
      { role: 'user', content: chat.question },
      { role: 'assistant', content: chat.answer }
    ];
    this.hasSubmittedOnce = true;
    this.menuOpen = false;
  }

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  scrollToBottom(): void {
    try {
      this.chatContainer.nativeElement.scrollTop = this.chatContainer.nativeElement.scrollHeight;
    } catch (err) {
      console.error('Scroll failed:', err);
    }
    this.chatContainer.nativeElement.scrollTo({
      top: this.chatContainer.nativeElement.scrollHeight,
      behavior: 'smooth'
    });
  }

  private renderPdfqaAnswer(res: AskStructuredResponse): string {
    const bullets = (res.bullets && res.bullets.length > 0)
      ? res.bullets.map(b => `- ${b}`).join('\n')
      : res.answer;
    const cites = (res.citations && res.citations.length > 0)
      ? res.citations.map(c => `(${c.fileName}, p. ${c.pageNumber})`).join(', ')
      : '';
    const lines = ['**PDF Answer**', bullets, cites ? `\nSources: ${cites}` : ''].filter(Boolean);
    return lines.join('\n');
  }

  // WORKFLOW STEP 4 — User sends a message
  // 1. Add user message to the screen
  // 2. Call backend AI to get an answer
  // 3. Once answer arrives, add it to screen
  // 4. Call loadChatHistory() to refresh the sidebar with the latest chat from Azure DB
  sendMessage(textarea: HTMLTextAreaElement) {
    const raw = this.userInput;
    if (!raw || !raw.trim()) return;

    const question = raw.trim();
    this.messages.push({ role: 'user', content: question });
    this.userInput = '';
    this.loading = true;
    if (!this.hasSubmittedOnce) this.hasSubmittedOnce = true;
    setTimeout(() => { textarea.style.height = 'auto'; });

    const account = this.authService.instance.getActiveAccount();
    const employeeId = account?.username || 'unknown';

    const addAssistant = (text: string, citations: { fileName: string; pageNumber: number; docPath: string }[] = []) => {
      const idx = this.messages.push({
        role: 'assistant',
        content: text,
        id: this.makeId(),
        feedback: null,
        citations
      }) - 1;
      this.loading = false;
      // NAVEEN - refresh sidebar after each answer so latest chat appears in history
      this.loadChatHistory();
      this.fetchFollowUps(idx, employeeId);
    };

    this.pdfqa.askStructured({ question, includeSummary: true }).subscribe({
      next: (res) => {
        const refusalText = "I am unable to answer that question right now. Please ask agency-related questions.";
        const isRefusal = typeof res.answer === 'string' && res.answer.includes(refusalText);
        if (isRefusal) {
          addAssistant(refusalText);
          return;
        }
        const rawAnswer = res.answer || '';
        const citations = Array.isArray(res.citations) ? res.citations : [];
        addAssistant(rawAnswer, citations);
      },
      error: (err) => {
        console.warn('PDF-QA failed:', err?.message || err);
        addAssistant("I am unable to answer that question. Please ask agency-related questions.2");
      }
    });
  }

  // Fetches 3 follow-up question suggestions from the backend after each answer
  private fetchFollowUps(i: number, employeeId: string = 'unknown') {
    const lastUser = this.messages.slice().reverse().find(m => m.role === 'user')?.content || '';
    const assistant = this.messages[i].content;

    this.http.post<FollowupsResponse>(
      'http://localhost:8081/api/followups',
      { user: lastUser, answer: assistant, employeeId },
      { headers: { 'Content-Type': 'application/json' } }
    ).subscribe({
      next: (res: FollowupsResponse) => {
        this.messages[i] = { ...this.messages[i], followUps: res.followUps?.slice(0, 3) || [] };
      },
      error: (err) => {
        console.warn('Follow-ups unavailable:', err?.message || err);
      }
    });
  }

  // Sends thumbs up or thumbs down feedback to the backend
  sendFeedback(verdict: 'up' | 'down', i: number) {
    const m = this.messages[i];
    if (!m || m.role !== 'assistant' || m.feedback) return;
    this.messages[i] = { ...m, feedback: verdict };
    const payload = {
      messageId: m.id || this.makeId(),
      verdict,
      agent: 'chat',
      userText: this.getLastUserBefore(i),
      assistantText: m.content,
      meta: { page: 'DCP Manual Search' }
    };
    this.http.post<{ status: string; receivedAt: string; messageId: string }>(
      'http://localhost:8081/api/feedback',
      payload,
      { headers: { 'Content-Type': 'application/json' } }
    ).subscribe({
      next: () => { },
      error: (err) => { console.warn('Feedback failed:', err?.message || err); }
    });
  }

  private getLastUserBefore(i: number): string {
    for (let j = i - 1; j >= 0; j--) {
      if (this.messages[j].role === 'user') return this.messages[j].content;
    }
    return '';
  }

  private makeId(): string {
    return 'm-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 6);
  }

  sendFollowUp(q: string) {
    this.userInput = q;
    const ta = this.chatInputRef?.nativeElement;
    if (ta) this.sendMessage(ta);
  }

  // Clears the screen only — Azure DB history is NOT deleted
  clearChat() {
    this.messages = [];
    this.hasSubmittedOnce = false;
  }

  toggleMenu() { this.menuOpen = !this.menuOpen; }
  goToWelcome() { this.router.navigate(['/']); }

  openPdf(): void {
    window.open('/assets/Division of Child Protection Casework Practice Requirements Manual December 6_2015.pdf', '_blank');
  }

  autoResize(textArea: HTMLTextAreaElement): void {
    textArea.style.height = 'auto';
    textArea.style.height = Math.min(textArea.scrollHeight, 120) + 'px';
  }

  pdfModalOpen = false;
  pdfModalSrc = '';
  pdfModalLabel = '';

  openPdfPage(docPath: string, pageNumber: number, fileName: string): void {
    const encoded = encodeURIComponent(docPath);
    this.pdfModalSrc = `http://localhost:8081/api/pdfqa/page-image?docPath=${encoded}&page=${pageNumber}`;
    this.pdfModalLabel = `${fileName} — Page ${pageNumber}`;
    this.pdfModalOpen = true;
  }

  closePdfModal(): void {
    this.pdfModalOpen = false;
    this.pdfModalSrc = '';
  }

  inputFocused = false;
  onFocus() { this.inputFocused = true; }
  onBlur() {
    setTimeout(() => {
      if (!this.userInput.trim()) this.inputFocused = false;
    }, 100);
  }

  async copy(text: string, i: number) {
    try {
      if (navigator.clipboard && (window as any).isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
      }
      this.copiedIndex = i;
      setTimeout(() => (this.copiedIndex = null), 1200);
    } catch (e) {
      console.error('Copy failed:', e);
    }
  }
}