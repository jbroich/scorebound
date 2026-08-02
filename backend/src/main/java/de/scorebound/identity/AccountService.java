package de.scorebound.identity;

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

	public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
			TemporaryPasswordGenerator temporaryPasswordGenerator) {
		this.accountRepository = accountRepository;
		this.passwordEncoder = passwordEncoder;
		this.temporaryPasswordGenerator = temporaryPasswordGenerator;
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

	@Transactional(readOnly = true)
	public Account requireAccount(UUID accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new IllegalArgumentException("Account does not exist"));
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
