package de.scorebound.identity;

import de.scorebound.teams.MemberRepository;
import de.scorebound.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AccountService {

	private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{3,64}");
	private static final int MINIMUM_PASSWORD_LENGTH = 12;
	private static final int MAXIMUM_PASSWORD_LENGTH = 128;

	private final AccountRepository accountRepository;
	private final PasswordEncoder passwordEncoder;
	private final TemporaryPasswordGenerator temporaryPasswordGenerator;
	private final MemberRepository memberRepository;

	public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
			TemporaryPasswordGenerator temporaryPasswordGenerator, MemberRepository memberRepository) {
		this.accountRepository = accountRepository;
		this.passwordEncoder = passwordEncoder;
		this.temporaryPasswordGenerator = temporaryPasswordGenerator;
		this.memberRepository = memberRepository;
	}

	@Transactional
	public CreatedAccount createWithTemporaryPassword(String username, Set<Role> roles,
			String preferredLocale, UUID actorId) {
		String normalizedUsername = normalizeUsername(username);
		validateRoles(roles);
		validateLocale(preferredLocale);
		if (accountRepository.findByNormalizedUsername(normalizedUsername).isPresent()) {
			throw new IllegalArgumentException("Username is already in use");
		}

		String temporaryPassword = temporaryPasswordGenerator.generate();
		Account account = Account.create(username.trim(), normalizedUsername,
				passwordEncoder.encode(temporaryPassword), roles, preferredLocale, actorId);
		return new CreatedAccount(accountRepository.save(account), temporaryPassword);
	}

	@Transactional
	public Account createBootstrapAdmin(String username, String password) {
		String normalizedUsername = normalizeUsername(username);
		validatePassword(password);
		return accountRepository.findByNormalizedUsername(normalizedUsername)
				.orElseGet(() -> accountRepository.save(Account.create(username.trim(), normalizedUsername,
						passwordEncoder.encode(password), Set.of(Role.ADMIN), "en", null)));
	}

	@Transactional
	public String issueTemporaryPassword(UUID accountId, UUID actorId) {
		Account account = requireAccount(accountId);
		String temporaryPassword = temporaryPasswordGenerator.generate();
		account.replacePassword(passwordEncoder.encode(temporaryPassword), true, actorId);
		return temporaryPassword;
	}

	@Transactional
	public void changePassword(UUID accountId, String currentPassword, String newPassword) {
		Account account = requireAccount(accountId);
		if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
			throw new IllegalArgumentException("Current password is invalid");
		}
		validatePassword(newPassword);
		if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
			throw new IllegalArgumentException("New password must be different");
		}
		account.replacePassword(passwordEncoder.encode(newPassword), false, accountId);
	}

	@Transactional
	public Account changeMemberLink(UUID accountId, UUID memberId, UUID actorId) {
		Account account = requireAccount(accountId);
		if (memberId != null) {
			if (!memberRepository.existsById(memberId)) {
				throw new ApiException(HttpStatus.NOT_FOUND, "resource_not_found",
						"Member does not exist");
			}
			if (accountRepository.existsByMemberIdAndIdNot(memberId, accountId)) {
				throw new ApiException(HttpStatus.CONFLICT, "member_already_linked",
						"Member is already linked to another account");
			}
		}
		account.changeMemberLink(memberId, actorId);
		return account;
	}

	@Transactional(readOnly = true)
	public Account requireAccount(UUID accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new IllegalArgumentException("Account does not exist"));
	}

	@Transactional(readOnly = true)
	public java.util.List<Account> listAccounts() {
		return accountRepository.findAllByOrderByUsernameAsc();
	}

	@Transactional
	public Account updateAdministration(UUID accountId, Boolean enabled, Set<Role> roles,
			String preferredLocale, boolean preferredLocalePresent, UUID memberId,
			boolean memberIdPresent, UUID actorId) {
		Account account = requireAccount(accountId);
		if (roles != null) {
			validateRoles(roles);
		}
		if (preferredLocalePresent) {
			validateLocale(preferredLocale);
		}
		if (memberIdPresent && memberId != null) {
			if (!memberRepository.existsById(memberId)) {
				throw new ApiException(HttpStatus.NOT_FOUND, "resource_not_found",
						"Member does not exist");
			}
			if (accountRepository.existsByMemberIdAndIdNot(memberId, accountId)) {
				throw new ApiException(HttpStatus.CONFLICT, "member_already_linked",
						"Member is already linked to another account");
			}
		}
		boolean removesOwnAdminAccess = accountId.equals(actorId)
				&& ((enabled != null && !enabled)
						|| (roles != null && !roles.contains(Role.ADMIN)));
		if (removesOwnAdminAccess) {
			throw new ApiException(HttpStatus.CONFLICT, "cannot_remove_own_admin_access",
					"Administrators cannot remove their own access");
		}
		account.updateAdministration(enabled, roles, preferredLocale, preferredLocalePresent,
				memberId, memberIdPresent, actorId);
		return account;
	}

	public static String normalizeUsername(String username) {
		if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
			throw new IllegalArgumentException("Username must contain 3 to 64 letters, numbers, dots, dashes, or underscores");
		}
		return username.trim().toLowerCase(Locale.ROOT);
	}

	private static void validateRoles(Set<Role> roles) {
		if (roles == null || roles.isEmpty()) {
			throw new IllegalArgumentException("At least one role is required");
		}
	}

	private static void validateLocale(String preferredLocale) {
		if (preferredLocale != null && !Set.of("en", "de").contains(preferredLocale)) {
			throw new IllegalArgumentException("Preferred locale must be en or de");
		}
	}

	private static void validatePassword(String password) {
		if (password == null || password.length() < MINIMUM_PASSWORD_LENGTH
				|| password.length() > MAXIMUM_PASSWORD_LENGTH) {
			throw new IllegalArgumentException("Password must contain 12 to 128 characters");
		}
	}

	public record CreatedAccount(Account account, String temporaryPassword) {
	}
}
