package de.scorebound.security;

import de.scorebound.identity.AccountRepository;
import de.scorebound.identity.AccountService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountUserDetailsService implements UserDetailsService {

	private final AccountRepository accountRepository;

	public AccountUserDetailsService(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		try {
			return accountRepository.findByNormalizedUsername(AccountService.normalizeUsername(username))
					.map(ScoreboundPrincipal::from)
					.orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
		} catch (IllegalArgumentException exception) {
			throw new UsernameNotFoundException("Invalid credentials", exception);
		}
	}
}
