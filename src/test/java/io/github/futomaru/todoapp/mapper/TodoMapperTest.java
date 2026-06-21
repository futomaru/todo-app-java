package io.github.futomaru.todoapp.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.futomaru.todoapp.entity.Todo;

@MybatisTest
class TodoMapperTest {

    @Autowired
    TodoMapper todoMapper;

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-06-21T10:00:00");

    private Todo buildTodo(String title, String description, boolean completed) {
        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setDescription(description);
        todo.setCompleted(completed);
        todo.setCreatedAt(NOW);
        todo.setUpdatedAt(NOW);
        return todo;
    }

    @Nested
    class findAll {

        @Test
        void 登録した2件が全件取得できる() {
            todoMapper.insert(buildTodo("買い物", "牛乳とパン", false));
            todoMapper.insert(buildTodo("部屋掃除", null, true));

            List<Todo> todos = todoMapper.findAll();

            assertThat(todos).hasSize(2);
        }

        @Test
        void 登録件数が0件のとき空リストを返す() {
            List<Todo> todos = todoMapper.findAll();

            assertThat(todos).isEmpty();
        }

        @Test
        void id昇順で返す() {
            todoMapper.insert(buildTodo("最初", null, false));
            todoMapper.insert(buildTodo("次", null, false));

            List<Todo> todos = todoMapper.findAll();

            assertThat(todos.get(0).getTitle()).isEqualTo("最初");
            assertThat(todos.get(1).getTitle()).isEqualTo("次");
        }
    }

    @Nested
    class findByCompleted {

        @Test
        void trueを指定すると完了済みのみ返す() {
            todoMapper.insert(buildTodo("未完了", null, false));
            todoMapper.insert(buildTodo("完了済み", null, true));

            List<Todo> result = todoMapper.findByCompleted(true);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isCompleted()).isTrue();
        }

        @Test
        void falseを指定すると未完了のみ返す() {
            todoMapper.insert(buildTodo("未完了", null, false));
            todoMapper.insert(buildTodo("完了済み", null, true));

            List<Todo> result = todoMapper.findByCompleted(false);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isCompleted()).isFalse();
        }

        @Test
        void 該当するステータスが存在しないとき空リストを返す() {
            todoMapper.insert(buildTodo("未完了のみ", null, false));

            List<Todo> result = todoMapper.findByCompleted(true);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class findById {

        @Test
        void 存在するidを指定すると該当のTodoを返す() {
            Todo todo = buildTodo("買い物", "牛乳", false);
            todoMapper.insert(todo);

            var result = todoMapper.findById(todo.getId());

            assertThat(result).hasValueSatisfying(t -> assertThat(t.getTitle()).isEqualTo("買い物"));
        }

        @Test
        void 存在しないidを指定するとOptional空を返す() {
            var result = todoMapper.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class insert {

        @Test
        void 登録後にidが採番されエンティティに設定される() {
            Todo todo = buildTodo("新規Todo", null, false);

            todoMapper.insert(todo);

            assertThat(todo.getId()).isNotNull();
        }

        @Test
        void descriptionがnullでも登録できる() {
            Todo todo = buildTodo("説明なし", null, false);

            todoMapper.insert(todo);

            assertThat(todoMapper.findById(todo.getId()))
                    .hasValueSatisfying(t -> assertThat(t.getDescription()).isNull());
        }
    }

    @Nested
    class update {

        @Test
        void titleとcompletedを変更すると反映される() {
            Todo todo = buildTodo("変更前", null, false);
            todoMapper.insert(todo);
            todo.setTitle("変更後");
            todo.setCompleted(true);
            todo.setUpdatedAt(NOW.plusHours(1));

            todoMapper.update(todo);

            assertThat(todoMapper.findById(todo.getId()))
                    .hasValueSatisfying(t -> {
                        assertThat(t.getTitle()).isEqualTo("変更後");
                        assertThat(t.isCompleted()).isTrue();
                    });
        }
    }

    @Nested
    class deleteById {

        @Test
        void 削除後にfindByIdが空を返す() {
            Todo todo = buildTodo("削除対象", null, false);
            todoMapper.insert(todo);

            todoMapper.deleteById(todo.getId());

            assertThat(todoMapper.findById(todo.getId())).isEmpty();
        }

        @Test
        void 削除対象以外のTodoは残る() {
            Todo target = buildTodo("削除対象", null, false);
            Todo other = buildTodo("残るTodo", null, false);
            todoMapper.insert(target);
            todoMapper.insert(other);

            todoMapper.deleteById(target.getId());

            List<Todo> remaining = todoMapper.findAll();
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getTitle()).isEqualTo("残るTodo");
        }
    }

    @Nested
    class deleteCompleted {

        @Test
        void 完了済みのみ削除され未完了は残る() {
            todoMapper.insert(buildTodo("未完了", null, false));
            todoMapper.insert(buildTodo("完了済み1", null, true));
            todoMapper.insert(buildTodo("完了済み2", null, true));

            todoMapper.deleteCompleted();

            List<Todo> remaining = todoMapper.findAll();
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).isCompleted()).isFalse();
        }

        @Test
        void 削除件数を返す() {
            todoMapper.insert(buildTodo("完了済み1", null, true));
            todoMapper.insert(buildTodo("完了済み2", null, true));

            int deleted = todoMapper.deleteCompleted();

            assertThat(deleted).isEqualTo(2);
        }

        @Test
        void 完了済みが0件のとき削除件数は0を返す() {
            todoMapper.insert(buildTodo("未完了", null, false));

            int deleted = todoMapper.deleteCompleted();

            assertThat(deleted).isEqualTo(0);
        }
    }
}
