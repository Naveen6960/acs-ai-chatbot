import { Routes } from '@angular/router';
import { WelcomePageComponent } from './welcome-page/welcome-page.component';
import { ChatBotComponent } from './chat-bot/chat-bot.component';
import { ToolTwoPlaceHolderComponent } from './tool-two-placeholder/tool-two-placeholder';
import { ToolThreePlaceholderComponent } from './tool-three-placeholder/tool-three-placeholder';
import { ToolFourPlaceholderComponent } from './tool-four-placeholder/tool-four-placeholder';

import { MsalGuard } from '@azure/msal-angular';

export const routes: Routes = [
  {
    path: 'auth',
    loadComponent: () =>
      import('@azure/msal-angular').then(m => m.MsalRedirectComponent)
  },

  { path: '', component: WelcomePageComponent, canActivate: [MsalGuard] },
  { path: 'chatbot', component: ChatBotComponent, canActivate: [MsalGuard] },
  { path: 'tool2', component: ToolTwoPlaceHolderComponent, canActivate: [MsalGuard] },
  { path: 'tool3', component: ToolThreePlaceholderComponent, canActivate: [MsalGuard] },
  { path: 'tool4', component: ToolFourPlaceholderComponent, canActivate: [MsalGuard] },

  { path: '**', redirectTo: '' }
];
