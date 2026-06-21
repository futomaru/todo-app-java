package io.github.futomaru.todoapp.mapper;

import io.github.futomaru.todoapp.entity.Todo;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TodoMapper {

  @Select("SELECT * FROM todos ORDER BY id")
  List<Todo> findAll();

  @Select("SELECT * FROM todos WHERE completed = #{completed} ORDER BY id")
  List<Todo> findByCompleted(@Param("completed") boolean completed);

  @Select("SELECT * FROM todos WHERE id = #{id}")
  Optional<Todo> findById(@Param("id") Long id);

  @Insert(
      """
            INSERT INTO todos (title, description, completed, created_at, updated_at)
            VALUES (#{title}, #{description}, #{completed}, #{createdAt}, #{updatedAt})
            """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(Todo todo);

  @Update(
      """
            UPDATE todos
            SET title       = #{title},
                description = #{description},
                completed   = #{completed},
                updated_at  = #{updatedAt}
            WHERE id = #{id}
            """)
  void update(Todo todo);

  @Delete("DELETE FROM todos WHERE id = #{id}")
  void deleteById(@Param("id") Long id);

  @Delete("DELETE FROM todos WHERE completed = TRUE")
  int deleteCompleted();
}
