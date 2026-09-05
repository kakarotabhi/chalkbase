import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MainLayout } from './layout/main-layout/main-layout';

@Component({
  selector: 'app-root',
  imports: [MainLayout],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<cb-main-layout />',
})
export class App {}
