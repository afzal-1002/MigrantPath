import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { environment } from '../../../../environments/environment';
import { DeleteAccountDialog } from './delete-account-dialog';

describe('DeleteAccountDialog', () => {
  let fixture: ComponentFixture<DeleteAccountDialog>;
  let component: DeleteAccountDialog;
  let httpMock: HttpTestingController;
  let closeSpy: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    closeSpy = vi.fn();
    await TestBed.configureTestingModule({
      imports: [DeleteAccountDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: { close: closeSpy } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DeleteAccountDialog);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('does not submit when the confirmation text is not exactly DELETE', () => {
    component['form'].setValue({ currentPassword: 'correct-horse', confirmation: 'delete' });
    component.confirmDelete();
    httpMock.expectNone(`${environment.apiBaseUrl}/account/delete`);
    expect(closeSpy).not.toHaveBeenCalled();
  });

  it('submits currentPassword + confirmation=DELETE and closes with true on success', () => {
    component['form'].setValue({ currentPassword: 'correct-horse', confirmation: 'DELETE' });
    component.confirmDelete();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/account/delete`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ currentPassword: 'correct-horse', confirmation: 'DELETE' });
    req.flush(null);

    expect(closeSpy).toHaveBeenCalledWith(true);
  });

  it('shows a server error and does not close on failure (e.g. wrong password)', () => {
    component['form'].setValue({ currentPassword: 'wrong', confirmation: 'DELETE' });
    component.confirmDelete();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/account/delete`);
    req.flush(
      { message: 'Current password is incorrect' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(component['serverError']()).toContain('incorrect');
    expect(closeSpy).not.toHaveBeenCalled();
  });

  it('cancel closes with false without calling the API', () => {
    component.cancel();
    httpMock.expectNone(`${environment.apiBaseUrl}/account/delete`);
    expect(closeSpy).toHaveBeenCalledWith(false);
  });
});
