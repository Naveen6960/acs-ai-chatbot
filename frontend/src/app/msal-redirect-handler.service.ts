import { Injectable, OnDestroy } from '@angular/core';
import { MsalService, MsalBroadcastService } from '@azure/msal-angular';
import { InteractionStatus } from '@azure/msal-browser';
import { Subject, takeUntil } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class MsalRedirectHandlerService implements OnDestroy {
  private readonly destroying$ = new Subject<void>();

  constructor(
    private msalService: MsalService,
    private msalBroadcastService: MsalBroadcastService
  ) {
    this.msalService.handleRedirectObservable().subscribe();

    this.msalBroadcastService.inProgress$
      .pipe(takeUntil(this.destroying$))
      .subscribe((status) => {
        if (status === InteractionStatus.None) {
          const accounts = this.msalService.instance.getAllAccounts();
          if (accounts.length === 0) {
            this.msalService.loginRedirect();
          }
        }
      });
  }

  ngOnDestroy(): void {
    this.destroying$.next();
    this.destroying$.complete();
  }
}