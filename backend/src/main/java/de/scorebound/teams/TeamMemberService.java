package de.scorebound.teams;

import de.scorebound.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TeamMemberService {

	private static final Pattern COLOR_PATTERN = Pattern.compile("#[0-9A-Fa-f]{6}");
	private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
	private static final int MAX_IMAGE_BYTES = 2_000_000;

	private final TeamRepository teamRepository;
	private final MemberRepository memberRepository;
	private final MembershipRepository membershipRepository;
	private final TeamImageRepository teamImageRepository;

	public TeamMemberService(TeamRepository teamRepository, MemberRepository memberRepository,
			MembershipRepository membershipRepository, TeamImageRepository teamImageRepository) {
		this.teamRepository = teamRepository;
		this.memberRepository = memberRepository;
		this.membershipRepository = membershipRepository;
		this.teamImageRepository = teamImageRepository;
	}

	@Transactional
	public Team createTeam(String name, String shortName, String color, UUID actorId) {
		String validatedName = requiredText(name, 100, "Team name");
		String validatedShortName = requiredText(shortName, 20, "Team short name");
		String validatedColor = validateColor(color);
		Team team = Team.create(validatedName, validatedShortName, validatedColor, actorId);
		ensureTeamKeysAvailable(team.getId(), validatedName, validatedShortName);
		return teamRepository.save(team);
	}

	@Transactional(readOnly = true)
	public List<Team> listActiveTeams() {
		return teamRepository.findByActiveTrueOrderByNameAsc();
	}

	@Transactional(readOnly = true)
	public Team requireTeam(UUID teamId) {
		return teamRepository.findById(teamId)
				.orElseThrow(() -> notFound("Team does not exist"));
	}

	@Transactional
	public Team updateTeam(UUID teamId, String name, String shortName, String color,
			Boolean active, UUID actorId) {
		Team team = requireTeam(teamId);
		String validatedName = name == null ? team.getName() : requiredText(name, 100, "Team name");
		String validatedShortName = shortName == null ? team.getShortName()
				: requiredText(shortName, 20, "Team short name");
		String validatedColor = color == null ? team.getColor() : validateColor(color);
		boolean targetActive = active == null ? team.isActive() : active;
		if (targetActive) {
			ensureTeamKeysAvailable(teamId, validatedName, validatedShortName);
		}
		team.update(validatedName, validatedShortName, validatedColor, active, actorId);
		return team;
	}

	@Transactional
	public void replaceTeamImage(UUID teamId, String contentType, byte[] data, UUID actorId) {
		requireTeam(teamId);
		validateImage(contentType, data);
		TeamImage image = teamImageRepository.findById(teamId)
				.orElseGet(() -> TeamImage.create(teamId, contentType, data, actorId));
		if (teamImageRepository.existsById(teamId)) {
			image.replace(contentType, data, actorId);
		}
		teamImageRepository.save(image);
	}

	@Transactional(readOnly = true)
	public TeamImage requireTeamImage(UUID teamId) {
		requireTeam(teamId);
		return teamImageRepository.findById(teamId)
				.orElseThrow(() -> notFound("Team image does not exist"));
	}

	@Transactional(readOnly = true)
	public boolean hasTeamImage(UUID teamId) {
		return teamImageRepository.existsById(teamId);
	}

	@Transactional
	public void deleteTeamImage(UUID teamId) {
		requireTeam(teamId);
		teamImageRepository.deleteById(teamId);
	}

	@Transactional
	public CreatedMember createMember(String displayName, String firstName, String lastName,
			UUID teamId, UUID actorId) {
		Team team = requireActiveTeam(teamId);
		Member member = memberRepository.save(Member.create(
				requiredText(displayName, 100, "Display name"),
				optionalText(firstName, 100, "First name"),
				optionalText(lastName, 100, "Last name"), actorId));
		Membership membership = membershipRepository.save(Membership.open(
				member.getId(), team.getId(), Instant.now(), actorId));
		return new CreatedMember(member, membership);
	}

	@Transactional(readOnly = true)
	public Page<Member> listMembers(int page, int size, boolean includeInactive) {
		if (page < 0 || size < 1 || size > 100) {
			throw validation("Page must be non-negative and size must be between 1 and 100");
		}
		PageRequest pageable = PageRequest.of(page, size, Sort.by("displayName").ascending());
		return includeInactive ? memberRepository.findAll(pageable)
				: memberRepository.findByActiveTrue(pageable);
	}

	@Transactional(readOnly = true)
	public Member requireMember(UUID memberId) {
		return memberRepository.findById(memberId)
				.orElseThrow(() -> notFound("Member does not exist"));
	}

	@Transactional(readOnly = true)
	public MemberDetails getMemberDetails(UUID memberId) {
		Member member = requireMember(memberId);
		List<Membership> history = membershipRepository.findByMemberIdOrderByValidFromDesc(memberId);
		return new MemberDetails(member, history);
	}

	@Transactional
	public Member updateMember(UUID memberId, String displayName, String firstName,
			String lastName, Boolean active, UUID actorId) {
		Member member = requireMember(memberId);
		String validatedDisplayName = displayName == null ? null
				: requiredText(displayName, 100, "Display name");
		String validatedFirstName = firstName == null ? null
				: optionalText(firstName, 100, "First name");
		String validatedLastName = lastName == null ? null
				: optionalText(lastName, 100, "Last name");
		member.update(validatedDisplayName, firstName != null && validatedFirstName == null ? "" : validatedFirstName,
				lastName != null && validatedLastName == null ? "" : validatedLastName, active, actorId);
		return member;
	}

	@Transactional
	public Membership changeTeam(UUID memberId, UUID teamId, Instant effectiveAt, UUID actorId) {
		memberRepository.findByIdForUpdate(memberId)
				.orElseThrow(() -> notFound("Member does not exist"));
		Team team = requireActiveTeam(teamId);
		Membership current = membershipRepository.findByOpenMembershipKey(memberId)
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
						"active_membership_required", "Member has no active membership"));
		if (current.getTeamId().equals(teamId)) {
			throw validation("Member already belongs to this team");
		}
		Instant changeTime = effectiveAt == null ? Instant.now() : effectiveAt;
		if (changeTime.isAfter(Instant.now())) {
			throw validation("Team changes cannot be scheduled in the future");
		}
		current.close(changeTime);
		membershipRepository.flush();
		return membershipRepository.save(Membership.open(memberId, team.getId(), changeTime, actorId));
	}

	private Team requireActiveTeam(UUID teamId) {
		Team team = requireTeam(teamId);
		if (!team.isActive()) {
			throw validation("An inactive team cannot receive new members");
		}
		return team;
	}

	private void ensureTeamKeysAvailable(UUID teamId, String name, String shortName) {
		if (teamRepository.existsByActiveNameKeyAndIdNot(Team.normalize(name), teamId)) {
			throw new ApiException(HttpStatus.CONFLICT, "active_team_name_conflict",
					"An active team already uses this name");
		}
		if (teamRepository.existsByActiveShortNameKeyAndIdNot(Team.normalize(shortName), teamId)) {
			throw new ApiException(HttpStatus.CONFLICT, "active_team_short_name_conflict",
					"An active team already uses this short name");
		}
	}

	private static String requiredText(String value, int maximumLength, String field) {
		if (value == null || value.isBlank() || value.trim().length() > maximumLength) {
			throw validation(field + " must contain 1 to " + maximumLength + " characters");
		}
		return value.trim();
	}

	private static String optionalText(String value, int maximumLength, String field) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if (value.trim().length() > maximumLength) {
			throw validation(field + " must contain at most " + maximumLength + " characters");
		}
		return value.trim();
	}

	private static String validateColor(String color) {
		if (color == null || !COLOR_PATTERN.matcher(color).matches()) {
			throw validation("Color must use #RRGGBB format");
		}
		return color.toUpperCase();
	}

	private static void validateImage(String contentType, byte[] data) {
		if (!IMAGE_TYPES.contains(contentType) || data == null || data.length == 0
				|| data.length > MAX_IMAGE_BYTES || !matchesSignature(contentType, data)) {
			throw validation("Image must be a PNG, JPEG, or WebP file up to 2 MB");
		}
	}

	private static boolean matchesSignature(String contentType, byte[] data) {
		return switch (contentType) {
			case "image/png" -> data.length >= 8
					&& data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47
					&& data[4] == 0x0d && data[5] == 0x0a && data[6] == 0x1a && data[7] == 0x0a;
			case "image/jpeg" -> data.length >= 3
					&& data[0] == (byte) 0xff && data[1] == (byte) 0xd8 && data[2] == (byte) 0xff;
			case "image/webp" -> data.length >= 12
					&& new String(data, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
					&& new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
			default -> false;
		};
	}

	private static ApiException validation(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", message);
	}

	private static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "resource_not_found", message);
	}

	public record CreatedMember(Member member, Membership membership) {
	}

	public record MemberDetails(Member member, List<Membership> memberships) {
	}
}
