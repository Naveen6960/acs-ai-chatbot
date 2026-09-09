import { Component, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';


@Component({
  selector: 'app-welcome-page',
  imports: [CommonModule],
  templateUrl: './welcome-page.component.html',
  styleUrl: './welcome-page.component.css'
})


export class WelcomePageComponent {
  constructor(private router: Router) {}
  
  goToChatBot(): void {
    this.router.navigate(['/chatbot']);
  }

  goToTool2(): void {
    this.router.navigate(['/tool2']);
  }

  goToTool3(): void {
    this.router.navigate(['/tool3']);
  }

  goToTool4(): void {
    this.router.navigate(['/tool4']);
  }

}
