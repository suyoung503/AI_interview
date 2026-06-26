INSERT IGNORE INTO categories (id, name) VALUES
(1, 'Java'),
(2, 'Spring'),
(3, '데이터베이스'),
(4, '네트워크'),
(5, '운영체제');

INSERT IGNORE INTO questions (id, category_id, content) VALUES
-- Java (10문제)
(1,  1, 'JVM의 메모리 구조(힙, 스택, 메서드 영역 등)에 대해 설명해주세요.'),
(2,  1, 'GC(Garbage Collection)의 동작 원리와 종류를 설명해주세요.'),
(3,  1, '인터페이스와 추상 클래스의 차이점은 무엇인가요?'),
(4,  1, 'Java에서 동기화(Synchronization)를 구현하는 방법을 설명해주세요.'),
(5,  1, 'equals()와 hashCode()를 함께 오버라이드해야 하는 이유는 무엇인가요?'),
(6,  1, 'Java의 제네릭(Generic)이란 무엇이며 왜 사용하나요?'),
(7,  1, 'checked exception과 unchecked exception의 차이를 설명해주세요.'),
(8,  1, 'Java Stream API의 특징과 장점을 설명해주세요.'),
(9,  1, '불변 객체(Immutable Object)란 무엇이고 왜 사용하나요?'),
(10, 1, 'String, StringBuilder, StringBuffer의 차이점은 무엇인가요?'),

-- Spring (10문제)
(11, 2, 'Spring IoC와 DI의 개념을 설명해주세요.'),
(12, 2, 'Spring Bean의 생명주기를 설명해주세요.'),
(13, 2, '@Component, @Service, @Repository, @Controller의 차이점은 무엇인가요?'),
(14, 2, 'Spring AOP(Aspect Oriented Programming)란 무엇인가요?'),
(15, 2, '@Transactional 어노테이션의 동작 원리를 설명해주세요.'),
(16, 2, 'Spring MVC의 요청 처리 흐름을 설명해주세요.'),
(17, 2, 'Spring Boot와 Spring Framework의 차이점은 무엇인가요?'),
(18, 2, 'JPA N+1 문제란 무엇이며 어떻게 해결하나요?'),
(19, 2, 'Spring Security의 인증 처리 흐름을 설명해주세요.'),
(20, 2, '@Bean과 @Component의 차이점은 무엇인가요?'),

-- 데이터베이스 (10문제)
(21, 3, '인덱스(Index)란 무엇이며 어떻게 동작하나요?'),
(22, 3, '트랜잭션의 ACID 속성을 설명해주세요.'),
(23, 3, '정규화(Normalization)의 목적과 1NF, 2NF, 3NF를 설명해주세요.'),
(24, 3, 'JOIN의 종류(INNER, LEFT, RIGHT, FULL)와 차이점을 설명해주세요.'),
(25, 3, '데이터베이스에서 교착상태(Deadlock)란 무엇이며 어떻게 방지하나요?'),
(26, 3, 'ORM이란 무엇이며 장단점은 무엇인가요?'),
(27, 3, '데이터베이스 격리 수준(Isolation Level)의 종류를 설명해주세요.'),
(28, 3, '클러스터형 인덱스와 비클러스터형 인덱스의 차이를 설명해주세요.'),
(29, 3, 'NoSQL과 RDB의 차이점과 각각의 사용 사례를 설명해주세요.'),
(30, 3, '쿼리 최적화 방법에 대해 아는 것을 설명해주세요.'),

-- 네트워크 (10문제)
(31, 4, 'TCP와 UDP의 차이점을 설명해주세요.'),
(32, 4, 'HTTP와 HTTPS의 차이점을 설명해주세요.'),
(33, 4, 'OSI 7계층 모델을 설명해주세요.'),
(34, 4, 'TCP 3-way handshake와 4-way handshake를 설명해주세요.'),
(35, 4, 'REST API의 특징과 설계 원칙을 설명해주세요.'),
(36, 4, 'DNS의 동작 원리를 설명해주세요.'),
(37, 4, '쿠키(Cookie)와 세션(Session)의 차이점을 설명해주세요.'),
(38, 4, 'CORS란 무엇이며 어떻게 해결하나요?'),
(39, 4, 'JWT(JSON Web Token)의 구조와 동작 원리를 설명해주세요.'),
(40, 4, 'HTTP 상태 코드(2xx, 3xx, 4xx, 5xx)의 종류와 의미를 설명해주세요.'),

-- 운영체제 (10문제)
(41, 5, '프로세스와 스레드의 차이점을 설명해주세요.'),
(42, 5, '컨텍스트 스위칭(Context Switching)이란 무엇이며 비용이 발생하는 이유는 무엇인가요?'),
(43, 5, '교착상태(Deadlock)의 발생 조건 4가지와 해결 방법을 설명해주세요.'),
(44, 5, '페이지 교체 알고리즘(LRU, FIFO, LFU 등)의 종류를 설명해주세요.'),
(45, 5, '동기/비동기, 블로킹/논블로킹의 차이를 설명해주세요.'),
(46, 5, '뮤텍스(Mutex)와 세마포어(Semaphore)의 차이를 설명해주세요.'),
(47, 5, '가상 메모리(Virtual Memory)란 무엇이며 왜 사용하나요?'),
(48, 5, 'CPU 스케줄링 알고리즘(FCFS, SJF, Round Robin 등)의 종류를 설명해주세요.'),
(49, 5, '캐시 메모리의 동작 원리와 지역성(Locality)에 대해 설명해주세요.'),
(50, 5, '인터럽트(Interrupt)란 무엇이며 처리 과정을 설명해주세요.');
