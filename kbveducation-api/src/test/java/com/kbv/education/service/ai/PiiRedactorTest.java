package com.kbv.education.service.ai;

import com.kbv.education.entity.Role;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Names, emails and phone numbers never reach the AI; students become their code-names. */
class PiiRedactorTest {

    private static User user(String first, String last, RoleType roleType, String codeName) {
        Role role = new Role();
        role.setName(roleType);
        User u = new User();
        u.setFirstName(first);
        u.setLastName(last);
        u.setRole(role);
        u.setCodeName(codeName);
        return u;
    }

    private final PiiRedactor redactor = PiiRedactor.forUsers(List.of(
            user("Aisha", "Rahman", RoleType.STUDENT, "S-0003"),
            user("Fatima", "Rahman", RoleType.PARENT, null),
            user("Joseph", "Mathew", RoleType.SUPER_ADMIN, null)));

    @Test
    void replacesStudentNamesWithTheirCodeName() {
        assertThat(redactor.redact("Hi, I'm Aisha Rahman. aisha here again"))
                .isEqualTo("Hi, I'm S-0003. S-0003 here again");
    }

    @Test
    void replacesOtherPeoplesNamesWithAPlaceholder() {
        assertThat(redactor.redact("My mum Fatima said Mr Joseph was right"))
                .isEqualTo("My mum [name] said Mr [name] was right");
    }

    @Test
    void aSurnameSharedWithAStudentKeepsTheStudentsCodeName() {
        assertThat(redactor.redact("Rahman")).isEqualTo("S-0003");
    }

    @Test
    void doesNotTouchNamesInsideLongerWords() {
        assertThat(redactor.redact("Josephine and Aishas")).isEqualTo("Josephine and Aishas");
    }

    @Test
    void removesEmailsAndPhoneNumbersButNotScores() {
        String out = redactor.redact("Mail me at aisha.r@example.com or +971 50 123 4567. I got 62/80 in 2024.");

        assertThat(out).contains("[email]").contains("[phone]").contains("62/80").contains("2024");
        assertThat(out).doesNotContain("example.com").doesNotContain("4567");
    }
}
