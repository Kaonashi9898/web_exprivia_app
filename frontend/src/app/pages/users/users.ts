import { ChangeDetectorRef, Component, ElementRef, ViewChild, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin, map, of, retry, switchMap, timer, timeout } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Gruppo, PasswordResetRequest, RegisterRequest, RuoloUtente, Utente } from '../../core/app.models';
import { apiErrorMessage } from '../../core/api-error.utils';
import { PasswordResetNotificationsService } from '../../core/password-reset-notifications.service';

const DOMAIN_ERROR = 'Dominio non autorizzato. Utilizzare esclusivamente un indirizzo @exprivia.com';
const EXPRIVIA_EMAIL_PATTERN = /^[a-z0-9]+(?:[._-][a-z0-9]+)*@exprivia\.com$/i;

type UserFilter = 'all' | RuoloUtente | `group:${number}`;

interface UserWithGroups extends Utente {
  groups: Gruppo[];
}

interface GroupWithUsers extends Gruppo {
  users: Utente[];
}

type UserConfirmAction =
  | { kind: 'rejectPasswordReset'; request: PasswordResetRequest }
  | { kind: 'deleteUser'; user: Utente }
  | { kind: 'deleteGroup'; group: Gruppo };

@Component({
  selector: 'app-users',
  imports: [FormsModule],
  templateUrl: './users.html',
  styleUrl: './users.css',
})
export class UsersComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly passwordResetNotifications = inject(PasswordResetNotificationsService);
  private readonly requestDateFormatter = new Intl.DateTimeFormat('it-IT', {
    dateStyle: 'short',
    timeStyle: 'short',
  });
  private createRefreshTimers: number[] = [];
  private initialRefreshTimers: number[] = [];
  @ViewChild('groupsManagerPanel') private groupsManagerPanel?: ElementRef<HTMLElement>;

  roles: RuoloUtente[] = ['USER', 'BUILDING_MANAGER', 'RECEPTION', 'ADMIN', 'GUEST'];
  users: UserWithGroups[] = [];
  groups: GroupWithUsers[] = [];
  passwordResetRequests: PasswordResetRequest[] = [];
  loading = false;
  saving = false;
  message = '';
  error = '';
  form: RegisterRequest = this.emptyForm();
  showPassword = false;
  userSearch = '';
  groupSearch = '';
  activeFilter: UserFilter = 'all';
  selectedUserForGroups: UserWithGroups | null = null;
  newGroupName = '';
  creatingGroup = false;
  groupActionInFlightId: number | null = null;
  renamingGroupId: number | null = null;
  renameGroupName = '';
  groupMembershipAction = '';
  showCreateUserModal = false;
  showCreateGroupModal = false;
  showPasswordResetModal = false;
  selectedPasswordResetRequest: PasswordResetRequest | null = null;
  temporaryPassword = '';
  showTemporaryPassword = false;
  passwordResetActionId: number | null = null;
  passwordResetModalError = '';
  createGroupModalError = '';
  pendingConfirmAction: UserConfirmAction | null = null;
  confirmActionBusy = false;
  confirmActionError = '';
  messageAutoDismiss = false;
  private messageDismissTimer: number | null = null;

  ngOnInit(): void {
    this.loadUsers();
    this.initialRefreshTimers = [600, 1600].map((delay) =>
      window.setTimeout(() => this.loadUsers(false), delay),
    );
  }

  ngOnDestroy(): void {
    this.clearCreateRefreshTimers();
    this.initialRefreshTimers.forEach((timerId) => window.clearTimeout(timerId));
    this.clearMessageDismissTimer();
  }

  loadUsers(clearError = true): void {
    this.loading = true;
    if (clearError) {
      this.error = '';
    }
    this.api
      .listUsers()
      .pipe(
        switchMap((users) =>
          this.api.listGroups().pipe(
            switchMap((groups) => {
              if (!groups.length) {
                return of({ users, groups: [] as GroupWithUsers[] });
              }

              return forkJoin(
                groups.map((group) =>
                  this.api.listUsersByGroup(group.id).pipe(
                    timeout(7000),
                    retry({ count: 1, delay: () => timer(250) }),
                  ),
                ),
              ).pipe(
                map((groupUsers) => ({
                  users,
                  groups: groups.map((group, index) => ({
                    ...group,
                    users: groupUsers[index] ?? [],
                  })),
                })),
              );
            }),
          ),
        ),
        switchMap(({ users, groups }) =>
          this.api.listPasswordResetRequests().pipe(
            map((passwordResetRequests) => ({ users, groups, passwordResetRequests })),
          ),
        ),
        retry({ count: 2, delay: () => timer(350) }),
        timeout(7000),
        finalize(() => {
          this.loading = false;
          this.refreshView();
        }),
      )
      .subscribe({
        next: ({ users, groups, passwordResetRequests }) => {
          this.groups = groups;
          this.passwordResetRequests = passwordResetRequests;
          this.users = users.map((user) => ({
            ...user,
            groups: groups
              .filter((group) => group.users.some((item) => item.id === user.id))
              .map((group) => ({ id: group.id, nome: group.nome })),
          }));
          this.syncUserNotificationBadge();
          if (this.selectedUserForGroups) {
            this.selectedUserForGroups =
              this.users.find((item) => item.id === this.selectedUserForGroups?.id) ?? null;
          }
          this.refreshView();
        },
        error: (err) => {
          this.error = apiErrorMessage(err, 'Impossibile caricare gli utenti.');
          this.refreshView();
        },
      });
  }

  openPasswordResetModal(request: PasswordResetRequest): void {
    if (!this.canCompletePasswordResetRequest(request)) {
      this.error = 'Non sei autorizzato a impostare una password temporanea per questa richiesta.';
      this.refreshView();
      return;
    }
    this.selectedPasswordResetRequest = request;
    this.temporaryPassword = '';
    this.showTemporaryPassword = false;
    this.passwordResetModalError = '';
    this.showPasswordResetModal = true;
    this.error = '';
    this.clearMessage();
    this.refreshView();
  }

  closePasswordResetModal(): void {
    if (this.passwordResetActionId !== null) {
      return;
    }
    this.showPasswordResetModal = false;
    this.selectedPasswordResetRequest = null;
    this.temporaryPassword = '';
    this.showTemporaryPassword = false;
    this.passwordResetModalError = '';
    this.refreshView();
  }

  submitPasswordReset(): void {
    const request = this.selectedPasswordResetRequest;
    const temporaryPassword = this.temporaryPassword.trim();
    if (!request) {
      return;
    }
    if (!temporaryPassword || temporaryPassword.length < 8) {
      this.passwordResetModalError = 'La password temporanea deve essere di almeno 8 caratteri.';
      this.refreshView();
      return;
    }

    this.passwordResetActionId = request.id;
    this.passwordResetModalError = '';
    this.error = '';
    this.clearMessage();
    this.api.completePasswordResetRequest(request.id, temporaryPassword).pipe(
      timeout(7000),
      finalize(() => {
        this.passwordResetActionId = null;
        this.refreshView();
      }),
    ).subscribe({
      next: () => {
        this.showPasswordResetModal = false;
        this.selectedPasswordResetRequest = null;
        this.temporaryPassword = '';
        this.showTemporaryPassword = false;
        this.passwordResetModalError = '';
        this.showSuccessMessage('Password temporanea impostata. Comunicala all\'utente tramite un canale sicuro.', true);
        this.removePasswordResetRequestFromBadge(request.id);
        this.loadUsers(false);
      },
      error: (err) => {
        this.passwordResetModalError = apiErrorMessage(err, 'Impostazione password temporanea non riuscita.');
        this.refreshView();
      },
    });
  }

  openRejectPasswordResetModal(request: PasswordResetRequest): void {
    if (!this.canRejectPasswordResetRequest(request)) {
      this.error = 'Non sei autorizzato a rifiutare questa richiesta.';
      this.refreshView();
      return;
    }

    this.openConfirmAction({ kind: 'rejectPasswordReset', request });
  }

  confirmPendingAction(): void {
    const action = this.pendingConfirmAction;
    if (!action || this.confirmActionBusy) {
      return;
    }

    if (action.kind === 'rejectPasswordReset') {
      this.confirmRejectPasswordResetRequest(action.request);
      return;
    }
    if (action.kind === 'deleteUser') {
      this.confirmDeleteUser(action.user);
      return;
    }
    this.confirmDeleteGroup(action.group);
  }

  closeConfirmActionModal(): void {
    if (this.confirmActionBusy) {
      return;
    }

    this.pendingConfirmAction = null;
    this.confirmActionError = '';
    this.refreshView();
  }

  confirmActionTitle(): string {
    const action = this.pendingConfirmAction;
    if (!action) {
      return '';
    }
    if (action.kind === 'rejectPasswordReset') {
      return 'Rifiutare richiesta reset password?';
    }
    if (action.kind === 'deleteUser') {
      return 'Eliminare account?';
    }
    return 'Eliminare gruppo?';
  }

  confirmActionMessage(): string {
    const action = this.pendingConfirmAction;
    if (!action) {
      return '';
    }
    if (action.kind === 'rejectPasswordReset') {
      return `Rifiutare la richiesta reset password per ${action.request.email}?`;
    }
    if (action.kind === 'deleteUser') {
      return `Eliminare ${action.user.fullName}?`;
    }
    return `Eliminare il gruppo ${action.group.nome}?`;
  }

  confirmActionButtonLabel(): string {
    const action = this.pendingConfirmAction;
    if (!action) {
      return 'Conferma';
    }
    return action.kind === 'rejectPasswordReset' ? 'Rifiuta' : 'Elimina';
  }

  confirmActionBusyLabel(): string {
    const action = this.pendingConfirmAction;
    if (!action) {
      return 'Operazione...';
    }
    return action.kind === 'rejectPasswordReset' ? 'Rifiuto...' : 'Eliminazione...';
  }

  private confirmRejectPasswordResetRequest(request: PasswordResetRequest): void {
    this.passwordResetActionId = request.id;
    this.error = '';
    this.confirmActionError = '';
    this.confirmActionBusy = true;
    this.clearMessage();
    this.api.rejectPasswordResetRequest(request.id).pipe(
      timeout(7000),
      finalize(() => {
        this.passwordResetActionId = null;
        this.confirmActionBusy = false;
        this.refreshView();
      }),
    ).subscribe({
      next: () => {
        if (this.selectedPasswordResetRequest?.id === request.id) {
          this.showPasswordResetModal = false;
          this.selectedPasswordResetRequest = null;
          this.temporaryPassword = '';
          this.showTemporaryPassword = false;
        }
        this.pendingConfirmAction = null;
        this.showSuccessMessage('Richiesta reset password rifiutata.', true);
        this.removePasswordResetRequestFromBadge(request.id);
        this.loadUsers(false);
      },
      error: (err) => {
        this.confirmActionError = apiErrorMessage(err, 'Rifiuto richiesta non riuscito.');
        this.refreshView();
      },
    });
  }

  createUser(): void {
    if (this.saving) {
      return;
    }

    const email = this.form.email.trim();
    if (!EXPRIVIA_EMAIL_PATTERN.test(email)) {
      this.error = DOMAIN_ERROR;
      this.clearMessage();
      this.refreshView();
      return;
    }

    this.saving = true;
    this.error = '';
    this.clearMessage();
    this.form = {
      ...this.form,
      fullName: this.form.fullName.trim(),
      email,
    };

    this.scheduleCreateRefresh();
    this.api
      .createUser(this.form)
      .pipe(
        timeout(7000),
        finalize(() => {
          this.saving = false;
          this.refreshView();
        }),
      )
      .subscribe({
        next: () => {
          this.clearCreateRefreshTimers();
          this.form = this.emptyForm();
          this.ensureFormRoleAllowed();
          this.showCreateUserModal = false;
          this.showSuccessMessage('Utente creato correttamente.');
          this.refreshView();
          this.loadUsers();
        },
        error: (err) => {
          this.clearCreateRefreshTimers();
          this.saving = false;
          this.error = apiErrorMessage(err, 'Creazione utente non riuscita.');
          this.refreshView();
          this.loadUsers(false);
        },
      });
  }

  updateRole(user: Utente, ruolo: RuoloUtente): void {
    if (!this.canEditRole(user) || !this.availableRoleOptionsForUser(user).includes(ruolo)) {
      this.error = 'Non sei autorizzato a modificare questo ruolo.';
      this.refreshView();
      return;
    }

    this.api.updateUserRole(user.id, ruolo).subscribe({
      next: (updated) => {
        this.users = this.users.map((item) => (item.id === updated.id ? { ...item, ...updated } : item));
        if (this.selectedUserForGroups?.id === updated.id) {
          this.selectedUserForGroups = this.users.find((item) => item.id === updated.id) ?? null;
        }
        this.syncUserNotificationBadge();
        this.showSuccessMessage('Ruolo aggiornato.', true);
        this.refreshView();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Aggiornamento ruolo non riuscito.');
        this.refreshView();
      },
    });
  }

  approveGuest(user: Utente): void {
    if (!this.canApproveGuest(user)) {
      this.error = 'Non sei autorizzato ad approvare questa richiesta.';
      this.refreshView();
      return;
    }

    this.api.updateUserRole(user.id, 'USER').subscribe({
      next: (updated) => {
        this.users = this.users.map((item) => (item.id === updated.id ? { ...item, ...updated } : item));
        this.syncUserNotificationBadge();
        this.showSuccessMessage("Richiesta approvata. L'utente ora ha ruolo USER.", true);
        this.refreshView();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Approvazione richiesta non riuscita.');
        this.refreshView();
      },
    });
  }

  deleteUser(user: Utente): void {
    if (!this.canDeleteUser(user)) {
      this.error = 'Non sei autorizzato a eliminare questo account.';
      this.refreshView();
      return;
    }

    this.openConfirmAction({ kind: 'deleteUser', user });
  }

  private confirmDeleteUser(user: Utente): void {
    this.confirmActionBusy = true;
    this.confirmActionError = '';
    this.api.deleteUser(user.id).subscribe({
      next: () => {
        this.users = this.users.filter((item) => item.id !== user.id);
        this.groups = this.groups.map((group) => ({
          ...group,
          users: group.users.filter((item) => item.id !== user.id),
        }));
        if (this.selectedUserForGroups?.id === user.id) {
          this.selectedUserForGroups = null;
        }
        this.pendingConfirmAction = null;
        this.confirmActionBusy = false;
        this.syncUserNotificationBadge();
        this.showSuccessMessage('Utente eliminato.');
        this.refreshView();
        this.loadUsers(false);
      },
      error: (err) => {
        this.confirmActionBusy = false;
        this.confirmActionError = apiErrorMessage(err, 'Eliminazione utente non riuscita.');
        this.refreshView();
      },
    });
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
    this.refreshView();
  }

  toggleTemporaryPasswordVisibility(): void {
    this.showTemporaryPassword = !this.showTemporaryPassword;
    this.refreshView();
  }

  filteredUsers(): UserWithGroups[] {
    const query = this.userSearch.trim().toLowerCase();
    return this.users.filter((user) => {
      const matchesFilter = this.matchesActiveFilter(user);
      if (!matchesFilter) {
        return false;
      }
      if (!query) {
        return true;
      }

      return [
        user.fullName,
        user.email,
        user.ruolo,
        ...user.groups.map((group) => group.nome),
      ].some((value) => value.toLowerCase().includes(query));
    });
  }

  filteredGroups(): GroupWithUsers[] {
    const query = this.groupSearch.trim().toLowerCase();
    if (!query) {
      return this.groups;
    }
    return this.groups.filter((group) => group.nome.toLowerCase().includes(query));
  }

  setActiveFilter(filter: UserFilter): void {
    this.activeFilter = this.activeFilter === filter ? 'all' : filter;
    this.refreshView();
  }

  isFilterActive(filter: UserFilter): boolean {
    return this.activeFilter === filter;
  }

  countByRole(ruolo: RuoloUtente): number {
    return this.users.filter((user) => user.ruolo === ruolo).length;
  }

  passwordResetNotificationCount(): number {
    return this.passwordResetRequests.length;
  }

  accountNotificationCount(): number {
    return this.users.filter((user) => user.ruolo === 'GUEST').length;
  }

  notificationBadgeLabel(count: number): string {
    return count > 99 ? '99+' : String(count);
  }

  countForFilter(filter: UserFilter): number {
    if (filter === 'all') {
      return this.users.length;
    }
    if (String(filter).startsWith('group:')) {
      const groupId = Number(String(filter).split(':')[1]);
      return this.groups.find((group) => group.id === groupId)?.users.length ?? 0;
    }
    return this.users.filter((user) => user.ruolo === filter).length;
  }

  openGroupsManager(user: UserWithGroups): void {
    if (!this.canManageGroups(user)) {
      this.error = 'Non sei autorizzato a gestire i gruppi di questo account.';
      this.refreshView();
      return;
    }
    this.selectedUserForGroups = user;
    this.groupMembershipAction = '';
    this.clearMessage();
    this.refreshView();
    window.setTimeout(() => {
      this.groupsManagerPanel?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 0);
  }

  closeGroupsManager(): void {
    this.selectedUserForGroups = null;
    this.groupMembershipAction = '';
    this.refreshView();
  }

  addSelectedUserToGroup(group: Gruppo): void {
    const user = this.selectedUserForGroups;
    if (!user || user.groups.some((item) => item.id === group.id)) {
      return;
    }

    this.groupMembershipAction = `add-${group.id}`;
    this.api.addUserToGroup(group.id, user.id).subscribe({
      next: () => {
        this.showSuccessMessage('Gruppo assegnato correttamente.');
        this.groupMembershipAction = '';
        this.loadUsers(false);
      },
      error: (err) => {
        this.groupMembershipAction = '';
        this.error = apiErrorMessage(err, 'Assegnazione gruppo non riuscita.');
        this.refreshView();
      },
    });
  }

  removeSelectedUserFromGroup(group: Gruppo): void {
    const user = this.selectedUserForGroups;
    if (!user || !user.groups.some((item) => item.id === group.id)) {
      return;
    }

    this.groupMembershipAction = `remove-${group.id}`;
    this.api.removeUserFromGroup(group.id, user.id).subscribe({
      next: () => {
        this.showSuccessMessage('Gruppo rimosso correttamente.');
        this.groupMembershipAction = '';
        this.loadUsers(false);
      },
      error: (err) => {
        this.groupMembershipAction = '';
        this.error = apiErrorMessage(err, 'Rimozione gruppo non riuscita.');
        this.refreshView();
      },
    });
  }

  createGroup(): void {
    const nome = this.newGroupName.trim();
    if (!nome || this.creatingGroup) {
      return;
    }

    this.creatingGroup = true;
    this.createGroupModalError = '';
    this.api.createGroup(nome).pipe(
      finalize(() => {
        this.creatingGroup = false;
        this.refreshView();
      }),
    ).subscribe({
      next: () => {
        this.newGroupName = '';
        this.showCreateGroupModal = false;
        this.createGroupModalError = '';
        this.showSuccessMessage('Gruppo creato correttamente.');
        this.loadUsers(false);
      },
      error: (err) => {
        this.createGroupModalError = apiErrorMessage(err, 'Creazione gruppo non riuscita.');
        this.refreshView();
      },
    });
  }

  startRenameGroup(group: Gruppo): void {
    this.renamingGroupId = group.id;
    this.renameGroupName = group.nome;
    this.refreshView();
  }

  cancelRenameGroup(): void {
    this.renamingGroupId = null;
    this.renameGroupName = '';
    this.refreshView();
  }

  saveRenameGroup(group: Gruppo): void {
    const nome = this.renameGroupName.trim();
    if (!nome) {
      return;
    }

    this.groupActionInFlightId = group.id;
    this.api.updateGroup(group.id, nome).pipe(
      finalize(() => {
        this.groupActionInFlightId = null;
        this.refreshView();
      }),
    ).subscribe({
      next: () => {
        this.renamingGroupId = null;
        this.renameGroupName = '';
        this.showSuccessMessage('Gruppo aggiornato.');
        this.loadUsers(false);
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Aggiornamento gruppo non riuscito.');
        this.refreshView();
      },
    });
  }

  deleteGroup(group: Gruppo): void {
    this.openConfirmAction({ kind: 'deleteGroup', group });
  }

  private confirmDeleteGroup(group: Gruppo): void {
    this.confirmActionBusy = true;
    this.confirmActionError = '';
    this.groupActionInFlightId = group.id;
    this.api.deleteGroup(group.id).pipe(
      finalize(() => {
        this.groupActionInFlightId = null;
        this.confirmActionBusy = false;
        this.refreshView();
      }),
    ).subscribe({
      next: () => {
        if (this.activeFilter === `group:${group.id}`) {
          this.activeFilter = 'all';
        }
        this.pendingConfirmAction = null;
        this.showSuccessMessage('Gruppo eliminato.');
        this.loadUsers(false);
      },
      error: (err) => {
        this.confirmActionError = apiErrorMessage(err, 'Eliminazione gruppo non riuscita.');
        this.refreshView();
      },
    });
  }

  availableRoleOptionsForNewUser(): RuoloUtente[] {
    if (this.isAdmin()) {
      return this.roles;
    }
    return ['USER', 'GUEST'];
  }

  availableRoleOptionsForUser(user: Utente): RuoloUtente[] {
    if (this.isAdmin()) {
      return this.roles;
    }
    if (!this.isReception() || !this.isReceptionManageableRole(user.ruolo)) {
      return [user.ruolo];
    }
    return ['USER', 'GUEST'];
  }

  canEditRole(user: Utente): boolean {
    if (this.isCurrentUser(user)) {
      return false;
    }
    return this.isAdmin() || (this.isReception() && this.isReceptionManageableRole(user.ruolo));
  }

  canDeleteUser(user: Utente): boolean {
    if (this.isCurrentUser(user)) {
      return false;
    }
    return this.isAdmin() || (this.isReception() && this.isReceptionManageableRole(user.ruolo));
  }

  canManageGroups(user: Utente): boolean {
    return this.isAdmin() || (this.isReception() && this.isReceptionManageableRole(user.ruolo));
  }

  canApproveGuest(user: Utente): boolean {
    return user.ruolo === 'GUEST' && this.canEditRole(user) && this.availableRoleOptionsForUser(user).includes('USER');
  }

  canCompletePasswordResetRequest(request: PasswordResetRequest): boolean {
    return !!request.userRole && !!request.userId && this.canManagePasswordResetTargetRole(request.userRole);
  }

  canRejectPasswordResetRequest(request: PasswordResetRequest): boolean {
    return !request.userRole || this.canManagePasswordResetTargetRole(request.userRole);
  }

  passwordResetActionBusy(requestId: number): boolean {
    return this.passwordResetActionId === requestId;
  }

  passwordResetTargetLabel(request: PasswordResetRequest): string {
    if (request.userFullName) {
      return request.userFullName;
    }
    return request.email;
  }

  passwordResetRoleLabel(request: PasswordResetRequest): string {
    return request.userRole ?? 'Utente non disponibile';
  }

  formatPasswordResetRequestedAt(value: string): string {
    return this.requestDateFormatter.format(new Date(value));
  }

  isGroupAssignedToSelectedUser(groupId: number): boolean {
    return this.selectedUserForGroups?.groups.some((item) => item.id === groupId) ?? false;
  }

  groupButtonLabel(user: UserWithGroups): string {
    return user.groups.length ? 'Gestisci gruppi' : 'Assegna gruppi';
  }

  groupMembershipBusy(action: 'add' | 'remove', groupId: number): boolean {
    return this.groupMembershipAction === `${action}-${groupId}`;
  }

  trackByUserId(_: number, user: Utente): number {
    return user.id;
  }

  trackByGroupId(_: number, group: Gruppo): number {
    return group.id;
  }

  openCreateUserModal(): void {
    this.form = this.emptyForm();
    this.ensureFormRoleAllowed();
    this.showPassword = false;
    this.showCreateUserModal = true;
    this.error = '';
    this.clearMessage();
    this.refreshView();
  }

  openCreateGroupModal(): void {
    this.newGroupName = '';
    this.createGroupModalError = '';
    this.showCreateGroupModal = true;
    this.error = '';
    this.clearMessage();
    this.refreshView();
  }

  closeCreateGroupModal(): void {
    if (this.creatingGroup) {
      return;
    }
    this.showCreateGroupModal = false;
    this.newGroupName = '';
    this.createGroupModalError = '';
    this.refreshView();
  }

  closeCreateUserModal(): void {
    if (this.saving) {
      return;
    }
    this.showCreateUserModal = false;
    this.form = this.emptyForm();
    this.ensureFormRoleAllowed();
    this.showPassword = false;
    this.refreshView();
  }

  activeFilterLabel(): string {
    if (this.activeFilter === 'all') {
      return 'Tutti gli account';
    }
    if (String(this.activeFilter).startsWith('group:')) {
      const groupId = Number(String(this.activeFilter).split(':')[1]);
      return this.groups.find((group) => group.id === groupId)?.nome ?? 'Gruppo selezionato';
    }
    return this.activeFilter;
  }

  clearActiveFilter(): void {
    this.activeFilter = 'all';
    this.refreshView();
  }

  private showSuccessMessage(message: string, autoDismiss = false): void {
    this.clearMessageDismissTimer();
    this.error = '';
    this.message = message;
    this.messageAutoDismiss = autoDismiss;
    if (autoDismiss) {
      this.messageDismissTimer = window.setTimeout(() => {
        if (this.message === message) {
          this.message = '';
          this.messageAutoDismiss = false;
          this.refreshView();
        }
      }, 5000);
    }
  }

  private removePasswordResetRequestFromBadge(requestId: number): void {
    this.passwordResetRequests = this.passwordResetRequests.filter((request) => request.id !== requestId);
    this.syncUserNotificationBadge();
  }

  private openConfirmAction(action: UserConfirmAction): void {
    this.pendingConfirmAction = action;
    this.confirmActionError = '';
    this.error = '';
    this.clearMessage();
    this.refreshView();
  }

  private syncUserNotificationBadge(): void {
    this.passwordResetNotifications.setFromData(this.passwordResetRequests, this.users);
  }

  private clearMessage(): void {
    this.clearMessageDismissTimer();
    this.message = '';
    this.messageAutoDismiss = false;
  }

  private clearMessageDismissTimer(): void {
    if (this.messageDismissTimer !== null) {
      window.clearTimeout(this.messageDismissTimer);
      this.messageDismissTimer = null;
    }
  }

  private emptyForm(): RegisterRequest {
    return {
      fullName: '',
      email: '',
      password: '',
      ruolo: this.isAdmin() ? 'USER' : 'GUEST',
    };
  }

  private scheduleCreateRefresh(): void {
    this.clearCreateRefreshTimers();
    this.createRefreshTimers = [1200, 3200].map((delay) =>
      window.setTimeout(() => {
        this.loadUsers(false);
        if (delay === 3200) {
          this.saving = false;
          this.refreshView();
        }
      }, delay),
    );
  }

  private clearCreateRefreshTimers(): void {
    this.createRefreshTimers.forEach((timerId) => window.clearTimeout(timerId));
    this.createRefreshTimers = [];
  }

  private ensureFormRoleAllowed(): void {
    const allowedRoles = this.availableRoleOptionsForNewUser();
    if (!allowedRoles.includes(this.form.ruolo)) {
      this.form.ruolo = allowedRoles[0];
    }
  }

  private matchesActiveFilter(user: UserWithGroups): boolean {
    if (this.activeFilter === 'all') {
      return true;
    }
    if (String(this.activeFilter).startsWith('group:')) {
      const groupId = Number(String(this.activeFilter).split(':')[1]);
      return user.groups.some((group) => group.id === groupId);
    }
    return user.ruolo === this.activeFilter;
  }

  isAdmin(): boolean {
    return this.auth.ruolo() === 'ADMIN';
  }

  isReception(): boolean {
    return this.auth.ruolo() === 'RECEPTION';
  }

  private canManagePasswordResetTargetRole(role: RuoloUtente): boolean {
    return this.isAdmin() || (this.isReception() && this.isReceptionManageableRole(role));
  }

  private isReceptionManageableRole(role: RuoloUtente): boolean {
    return role === 'USER' || role === 'GUEST';
  }

  private isCurrentUser(user: Utente): boolean {
    const currentUser = this.auth.currentUser();
    return !!currentUser?.email && currentUser.email.toLowerCase() === user.email.toLowerCase();
  }

  private refreshView(): void {
    this.cdr.detectChanges();
  }
}
