import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin, of, retry, switchMap, timer, timeout } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Gruppo, RegisterRequest, RuoloUtente, Utente } from '../../core/app.models';
import { apiErrorMessage } from '../../core/api-error.utils';

type UserFilter = 'all' | RuoloUtente | `group:${number}`;

interface UserWithGroups extends Utente {
  groups: Gruppo[];
}

interface GroupWithUsers extends Gruppo {
  users: Utente[];
}

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
  private createRefreshTimers: number[] = [];
  private initialRefreshTimers: number[] = [];

  roles: RuoloUtente[] = ['USER', 'BUILDING_MANAGER', 'RECEPTION', 'ADMIN', 'GUEST'];
  users: UserWithGroups[] = [];
  groups: GroupWithUsers[] = [];
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

  ngOnInit(): void {
    this.loadUsers();
    this.initialRefreshTimers = [600, 1600].map((delay) =>
      window.setTimeout(() => this.loadUsers(false), delay),
    );
  }

  ngOnDestroy(): void {
    this.clearCreateRefreshTimers();
    this.initialRefreshTimers.forEach((timerId) => window.clearTimeout(timerId));
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
                switchMap((groupUsers) =>
                  of({
                    users,
                    groups: groups.map((group, index) => ({
                      ...group,
                      users: groupUsers[index] ?? [],
                    })),
                  }),
                ),
              );
            }),
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
        next: ({ users, groups }) => {
          this.groups = groups;
          this.users = users.map((user) => ({
            ...user,
            groups: groups
              .filter((group) => group.users.some((item) => item.id === user.id))
              .map((group) => ({ id: group.id, nome: group.nome })),
          }));
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

  createUser(): void {
    if (this.saving) {
      return;
    }

    this.saving = true;
    this.error = '';
    this.message = '';

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
          this.message = 'Utente creato correttamente.';
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
        this.message = 'Ruolo aggiornato.';
        this.refreshView();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Aggiornamento ruolo non riuscito.');
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
    if (!confirm(`Eliminare ${user.fullName}?`)) {
      return;
    }

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
        this.message = 'Utente eliminato.';
        this.refreshView();
        this.loadUsers(false);
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Eliminazione utente non riuscita.');
        this.refreshView();
      },
    });
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
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
    this.activeFilter = filter;
    this.refreshView();
  }

  isFilterActive(filter: UserFilter): boolean {
    return this.activeFilter === filter;
  }

  countByRole(ruolo: RuoloUtente): number {
    return this.users.filter((user) => user.ruolo === ruolo).length;
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
    this.message = '';
    this.refreshView();
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
        this.message = 'Gruppo assegnato correttamente.';
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
        this.message = 'Gruppo rimosso correttamente.';
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
    this.api.createGroup(nome).pipe(
      finalize(() => {
        this.creatingGroup = false;
        this.refreshView();
      }),
    ).subscribe({
      next: () => {
        this.newGroupName = '';
        this.message = 'Gruppo creato correttamente.';
        this.loadUsers(false);
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Creazione gruppo non riuscita.');
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
        this.message = 'Gruppo aggiornato.';
        this.loadUsers(false);
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Aggiornamento gruppo non riuscito.');
        this.refreshView();
      },
    });
  }

  deleteGroup(group: Gruppo): void {
    if (!confirm(`Eliminare il gruppo ${group.nome}?`)) {
      return;
    }

    this.groupActionInFlightId = group.id;
    this.api.deleteGroup(group.id).pipe(
      finalize(() => {
        this.groupActionInFlightId = null;
        this.refreshView();
      }),
    ).subscribe({
      next: () => {
        if (this.activeFilter === `group:${group.id}`) {
          this.activeFilter = 'all';
        }
        this.message = 'Gruppo eliminato.';
        this.loadUsers(false);
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Eliminazione gruppo non riuscita.');
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
    return this.isAdmin() || (this.isReception() && this.isReceptionManageableRole(user.ruolo));
  }

  canDeleteUser(user: Utente): boolean {
    return this.isAdmin() || (this.isReception() && this.isReceptionManageableRole(user.ruolo));
  }

  canManageGroups(user: Utente): boolean {
    return this.isAdmin() || (this.isReception() && this.isReceptionManageableRole(user.ruolo));
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

  private isReceptionManageableRole(role: RuoloUtente): boolean {
    return role === 'USER' || role === 'GUEST';
  }

  private refreshView(): void {
    this.cdr.detectChanges();
  }
}
