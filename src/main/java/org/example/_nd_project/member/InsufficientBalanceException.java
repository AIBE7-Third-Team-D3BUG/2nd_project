package org.example._nd_project.member;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(int availableMinutes, int requiredMinutes) {
        super("보유 재화가 부족합니다. 현재 " + (availableMinutes / 30)
                + "품을 사용할 수 있으며 " + (requiredMinutes / 30) + "품이 필요합니다.");
    }
}
