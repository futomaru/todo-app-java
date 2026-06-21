package io.github.futomaru.todoapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.futomaru.todoapp.dto.TodoCreateRequest;
import io.github.futomaru.todoapp.dto.TodoResponse;
import io.github.futomaru.todoapp.dto.TodoUpdateRequest;
import io.github.futomaru.todoapp.entity.Todo;
import io.github.futomaru.todoapp.exception.TodoNotFoundException;
import io.github.futomaru.todoapp.mapper.TodoMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

  static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-20T10:00:00Z"), ZoneOffset.UTC);

  static final LocalDateTime FIXED_NOW = LocalDateTime.now(FIXED);

  @Mock TodoMapper mapper;

  TodoService service;

  @BeforeEach
  void setUp() {
    service = new TodoService(mapper, FIXED);
  }

  private Todo buildTodo(Long id, String title, String description, boolean completed) {
    LocalDateTime ts = LocalDateTime.parse("2026-06-20T09:00:00");
    Todo todo = new Todo();
    todo.setId(id);
    todo.setTitle(title);
    todo.setDescription(description);
    todo.setCompleted(completed);
    todo.setCreatedAt(ts);
    todo.setUpdatedAt(ts);
    return todo;
  }

  @Nested
  class findAll {

    @Test
    void completedがnullのときは全件取得する() {
      service.findAll(null);

      verify(mapper).findAll();
      verify(mapper, never()).findByCompleted(anyBoolean());
    }

    @Test
    void completedがtrueのときは完了済みのみ取得する() {
      service.findAll(true);

      verify(mapper).findByCompleted(true);
      verify(mapper, never()).findAll();
    }

    @Test
    void completedがfalseのときは未完了のみ取得する() {
      service.findAll(false);

      verify(mapper).findByCompleted(false);
      verify(mapper, never()).findAll();
    }

    @Test
    void MapperがかえしたTodoをTodoResponseに変換する() {
      Todo todo1 = buildTodo(1L, "買い物", "牛乳", false);
      Todo todo2 = buildTodo(2L, "掃除", null, true);
      when(mapper.findAll()).thenReturn(List.of(todo1, todo2));

      List<TodoResponse> result = service.findAll(null);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).id()).isEqualTo(1L);
      assertThat(result.get(0).title()).isEqualTo("買い物");
      assertThat(result.get(0).description()).isEqualTo("牛乳");
      assertThat(result.get(0).completed()).isFalse();
      assertThat(result.get(1).id()).isEqualTo(2L);
      assertThat(result.get(1).title()).isEqualTo("掃除");
      assertThat(result.get(1).description()).isNull();
      assertThat(result.get(1).completed()).isTrue();
    }

    @Test
    void Mapperが空リストを返したときは空リストを返す() {
      when(mapper.findAll()).thenReturn(List.of());

      List<TodoResponse> result = service.findAll(null);

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class findById {

    @Test
    void 存在するidを指定するとTodoResponseを返す() {
      Todo todo = buildTodo(1L, "買い物", "牛乳", false);
      when(mapper.findById(1L)).thenReturn(Optional.of(todo));

      TodoResponse result = service.findById(1L);

      assertThat(result).isNotNull();
      assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void MapperがかえしたTodoの各フィールドをTodoResponseに転写する() {
      LocalDateTime created = LocalDateTime.parse("2026-06-19T09:00:00");
      LocalDateTime updated = LocalDateTime.parse("2026-06-19T10:00:00");
      Todo todo = new Todo();
      todo.setId(1L);
      todo.setTitle("買い物");
      todo.setDescription("牛乳");
      todo.setCompleted(true);
      todo.setCreatedAt(created);
      todo.setUpdatedAt(updated);
      when(mapper.findById(1L)).thenReturn(Optional.of(todo));

      TodoResponse result = service.findById(1L);

      assertThat(result.id()).isEqualTo(1L);
      assertThat(result.title()).isEqualTo("買い物");
      assertThat(result.description()).isEqualTo("牛乳");
      assertThat(result.completed()).isTrue();
      assertThat(result.createdAt()).isEqualTo(created);
      assertThat(result.updatedAt()).isEqualTo(updated);
    }

    @Test
    void 存在しないidを指定するとTodoNotFoundExceptionをスローする() {
      when(mapper.findById(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.findById(999L)).isInstanceOf(TodoNotFoundException.class);
    }
  }

  @Nested
  class create {

    @Test
    void requestのtitleとdescriptionでinsertが呼ばれる() {
      TodoCreateRequest request = new TodoCreateRequest("買い物", "牛乳");

      service.create(request);

      ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
      verify(mapper).insert(captor.capture());
      assertThat(captor.getValue().getTitle()).isEqualTo("買い物");
      assertThat(captor.getValue().getDescription()).isEqualTo("牛乳");
    }

    @Test
    void completedはfalseで初期化される() {
      TodoCreateRequest request = new TodoCreateRequest("買い物", "牛乳");

      service.create(request);

      ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
      verify(mapper).insert(captor.capture());
      assertThat(captor.getValue().isCompleted()).isFalse();
    }

    @Test
    void createdAtとupdatedAtにClockの時刻が設定される() {
      TodoCreateRequest request = new TodoCreateRequest("買い物", "牛乳");

      service.create(request);

      ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
      verify(mapper).insert(captor.capture());
      assertThat(captor.getValue().getCreatedAt()).isEqualTo(FIXED_NOW);
      assertThat(captor.getValue().getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void descriptionがnullのrequestでもinsertされる() {
      TodoCreateRequest request = new TodoCreateRequest("買い物", null);

      service.create(request);

      ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
      verify(mapper).insert(captor.capture());
      assertThat(captor.getValue().getDescription()).isNull();
    }

    @Test
    void insertしたTodoを変換したTodoResponseを返す() {
      doAnswer(
              invocation -> {
                Todo todo = invocation.getArgument(0);
                todo.setId(42L);
                return null;
              })
          .when(mapper)
          .insert(any(Todo.class));
      TodoCreateRequest request = new TodoCreateRequest("買い物", "牛乳");

      TodoResponse result = service.create(request);

      assertThat(result.id()).isEqualTo(42L);
      assertThat(result.title()).isEqualTo("買い物");
      assertThat(result.description()).isEqualTo("牛乳");
      assertThat(result.completed()).isFalse();
      assertThat(result.createdAt()).isEqualTo(FIXED_NOW);
      assertThat(result.updatedAt()).isEqualTo(FIXED_NOW);
    }
  }

  @Nested
  class update {

    @Test
    void 存在しないidを指定するとTodoNotFoundExceptionをスローしupdateは呼ばれない() {
      when(mapper.findById(999L)).thenReturn(Optional.empty());
      TodoUpdateRequest request = new TodoUpdateRequest("新タイトル", null, null);

      assertThatThrownBy(() -> service.update(999L, request))
          .isInstanceOf(TodoNotFoundException.class);

      verify(mapper, never()).update(any());
    }

    @Test
    void titleのみ指定したときtitleだけ更新される() {
      Todo existing = buildTodo(1L, "旧タイトル", "旧説明", false);
      when(mapper.findById(1L)).thenReturn(Optional.of(existing));
      TodoUpdateRequest request = new TodoUpdateRequest("新タイトル", null, null);

      service.update(1L, request);

      ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
      verify(mapper).update(captor.capture());
      assertThat(captor.getValue().getTitle()).isEqualTo("新タイトル");
      assertThat(captor.getValue().getDescription()).isEqualTo("旧説明");
      assertThat(captor.getValue().isCompleted()).isFalse();
    }

    @Test
    void descriptionのみ指定したときdescriptionだけ更新される() {
      Todo existing = buildTodo(1L, "旧タイトル", "旧説明", false);
      when(mapper.findById(1L)).thenReturn(Optional.of(existing));
      TodoUpdateRequest request = new TodoUpdateRequest(null, "新説明", null);

      service.update(1L, request);

      ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
      verify(mapper).update(captor.capture());
      assertThat(captor.getValue().getTitle()).isEqualTo("旧タイトル");
      assertThat(captor.getValue().getDescription()).isEqualTo("新説明");
      assertThat(captor.getValue().isCompleted()).isFalse();
    }

    @Test
    void completedのみ指定したときcompletedだけ更新される() {
      Todo existing = buildTodo(1L, "旧タイトル", "旧説明", false);
      when(mapper.findById(1L)).thenReturn(Optional.of(existing));
      TodoUpdateRequest request = new TodoUpdateRequest(null, null, true);

      service.update(1L, request);

      ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
      verify(mapper).update(captor.capture());
      assertThat(captor.getValue().getTitle()).isEqualTo("旧タイトル");
      assertThat(captor.getValue().getDescription()).isEqualTo("旧説明");
      assertThat(captor.getValue().isCompleted()).isTrue();
    }

    @Test
    void すべてのフィールドがnullのとき既存値は維持されupdatedAtのみ更新される() {
      Todo existing = buildTodo(1L, "旧タイトル", "旧説明", false);
      when(mapper.findById(1L)).thenReturn(Optional.of(existing));
      TodoUpdateRequest request = new TodoUpdateRequest(null, null, null);

      service.update(1L, request);

      ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
      verify(mapper).update(captor.capture());
      assertThat(captor.getValue().getTitle()).isEqualTo("旧タイトル");
      assertThat(captor.getValue().getDescription()).isEqualTo("旧説明");
      assertThat(captor.getValue().isCompleted()).isFalse();
      assertThat(captor.getValue().getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void updatedAtにClockの時刻が設定される() {
      Todo existing = buildTodo(1L, "旧タイトル", "旧説明", false);
      when(mapper.findById(1L)).thenReturn(Optional.of(existing));
      TodoUpdateRequest request = new TodoUpdateRequest("新タイトル", null, null);

      service.update(1L, request);

      ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
      verify(mapper).update(captor.capture());
      assertThat(captor.getValue().getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void createdAtは更新されない() {
      Todo existing = buildTodo(1L, "旧タイトル", "旧説明", false);
      LocalDateTime originalCreatedAt = existing.getCreatedAt();
      when(mapper.findById(1L)).thenReturn(Optional.of(existing));
      TodoUpdateRequest request = new TodoUpdateRequest("新タイトル", null, null);

      service.update(1L, request);

      ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
      verify(mapper).update(captor.capture());
      assertThat(captor.getValue().getCreatedAt()).isEqualTo(originalCreatedAt);
    }

    @Test
    void 更新後のTodoを変換したTodoResponseを返す() {
      Todo existing = buildTodo(1L, "旧タイトル", "旧説明", false);
      when(mapper.findById(1L)).thenReturn(Optional.of(existing));
      TodoUpdateRequest request = new TodoUpdateRequest("新タイトル", null, true);

      TodoResponse result = service.update(1L, request);

      assertThat(result.id()).isEqualTo(1L);
      assertThat(result.title()).isEqualTo("新タイトル");
      assertThat(result.description()).isEqualTo("旧説明");
      assertThat(result.completed()).isTrue();
      assertThat(result.updatedAt()).isEqualTo(FIXED_NOW);
    }
  }

  @Nested
  class delete {

    @Test
    void 存在するidを指定するとdeleteByIdが呼ばれる() {
      Todo existing = buildTodo(1L, "削除対象", null, false);
      when(mapper.findById(1L)).thenReturn(Optional.of(existing));

      service.delete(1L);

      verify(mapper).deleteById(1L);
    }

    @Test
    void 存在しないidを指定するとTodoNotFoundExceptionをスローしdeleteByIdは呼ばれない() {
      when(mapper.findById(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.delete(999L)).isInstanceOf(TodoNotFoundException.class);

      verify(mapper, never()).deleteById(any());
    }
  }

  @Nested
  class deleteCompleted {

    @Test
    void MapperのdeleteCompletedが呼ばれる() {
      service.deleteCompleted();

      verify(mapper).deleteCompleted();
    }
  }
}
