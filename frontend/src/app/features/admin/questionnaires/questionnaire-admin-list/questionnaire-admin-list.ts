import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  AdminQuestionnaireService,
  AdminQuestionnaireSummary,
} from '../../../../core/services/admin/admin-questionnaire.service';

/** Route: /admin/questionnaires (brief §49). */
@Component({
  selector: 'app-questionnaire-admin-list',
  imports: [RouterLink],
  templateUrl: './questionnaire-admin-list.html',
})
export class QuestionnaireAdminList {
  private readonly questionnaireService = inject(AdminQuestionnaireService);

  protected readonly questionnaires = signal<AdminQuestionnaireSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  constructor() {
    this.questionnaireService.list().subscribe({
      next: (q) => {
        this.questionnaires.set(q);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }
}
