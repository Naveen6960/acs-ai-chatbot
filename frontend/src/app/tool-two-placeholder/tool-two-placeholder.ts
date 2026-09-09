import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MsalService } from '@azure/msal-angular';

interface Message {
  role: 'user' | 'assistant';
  content: string;
}

@Component({
  selector: 'app-tool-two-placeholder',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tool-two-placeholder.html',
  styleUrls: ['./tool-two-placeholder.css']
})
export class ToolTwoPlaceHolderComponent implements OnInit {
  messages: Message[] = [];
  userInput = '';
  loading = false;

  constructor(private http: HttpClient, private authService: MsalService) {}

  ngOnInit(): void {
    const accounts = this.authService.instance.getAllAccounts();
    if (accounts.length > 0) {
      this.authService.instance.setActiveAccount(accounts[0]);
    }
  }

  sendMessage() {
    if (!this.userInput.trim()) return;
    const userMsg: Message = { role: 'user', content: this.userInput };
    this.messages.push(userMsg);
    const prompt = this.userInput;
    this.userInput = '';
    this.loading = true;

    const account = this.authService.instance.getActiveAccount();
    const employeeId = account?.username || 'unknown';

    console.log('Logged in user:', employeeId);

    this.http.post<{response: string}>(
      'http://localhost:8081/api/chat',
      { prompt, agent: 'chat', employeeId },
      { headers: { 'Content-Type': 'application/json' } }
    ).subscribe({
      next: (res) => {
        this.messages.push({ role: 'assistant', content: res.response });
        this.loading = false;
      },
      error: (err) => {
        console.error('Full Error:', err);
        this.messages.push({ role: 'assistant', content: 'Error: ' + (err.message || JSON.stringify(err)) });
        this.loading = false;
      }
    });
  }

  clearChat() {
    this.messages = [];
  }
}