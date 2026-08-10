import { flushPromises, mount } from '@vue/test-utils'
import JobCommandDialog from './JobCommandDialog.vue'

describe('JobCommandDialog', () => {
  afterEach(() => { document.body.innerHTML = '' })

  it('requires an audited reason before cancelling', async () => {
    const wrapper = mount(JobCommandDialog, {
      attachTo: document.body,
      props: { modelValue: true, action: 'CANCEL', currentPriority: 'NORMAL', submitting: false }
    })
    await flushPromises()

    const confirm = [...document.body.querySelectorAll('button')]
      .find(button => button.textContent?.includes('确认执行')) as HTMLButtonElement
    confirm.click()
    await wrapper.vm.$nextTick()

    expect(document.body.textContent).toContain('请填写操作原因')
    expect(wrapper.emitted('submit')).toBeUndefined()
    wrapper.unmount()
  })

  it('emits the normalized reason for a controlled command', async () => {
    const wrapper = mount(JobCommandDialog, {
      attachTo: document.body,
      props: { modelValue: true, action: 'CANCEL', currentPriority: 'NORMAL', submitting: false }
    })
    await flushPromises()
    const textarea = document.body.querySelector('textarea') as HTMLTextAreaElement
    textarea.value = '  重复任务  '
    textarea.dispatchEvent(new Event('input', { bubbles: true }))
    await wrapper.vm.$nextTick()
    const confirm = [...document.body.querySelectorAll('button')]
      .find(button => button.textContent?.includes('确认执行')) as HTMLButtonElement
    confirm.click()
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('submit')?.[0]).toEqual([{ action: 'CANCEL', reason: '重复任务' }])
    wrapper.unmount()
  })
})
